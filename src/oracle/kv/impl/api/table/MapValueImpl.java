/*-
 * Copyright (C) 2011, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This file was distributed by Oracle as part of a version of Oracle NoSQL
 * Database made available at:
 *
 * http://www.oracle.com/technetwork/database/database-technologies/nosqldb/downloads/index.html
 *
 * Please see the LICENSE file included in the top-level directory of the
 * appropriate version of Oracle NoSQL Database for a copy of the license and
 * additional information.
 */

package oracle.kv.impl.api.table;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import oracle.kv.table.ArrayValue;
import oracle.kv.table.FieldDef;
import oracle.kv.table.FieldValue;
import oracle.kv.table.MapDef;
import oracle.kv.table.MapValue;
import oracle.kv.table.RecordValue;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

import com.sleepycat.persist.model.Persistent;

/**
 * MapValueImpl implements the MapValue interface and is a container object
 * that holds a map of FieldValue objects all of the same type.  The getters
 * and setters use the same semantics as Java Map.
 *
 * TODO: JSON: if duplicate values are to be handled in some cases one option is
 * to do this:
 *  1. detect the duplicates on put by looking at the return value. If not null,
 * save duplicates in a standalone list attached to this object, to be
 * serialized separately.
 *  2. on toString() output, or any output that might want to see all dups,
 * output the dups first (because last-in wins).
 *  3. normal map operations work on the actual copies and will not create dups.
 */
@Persistent(version=1)
public class MapValueImpl extends ComplexValueImpl implements MapValue {

    private static final long serialVersionUID = 1L;

    private final Map<String, FieldValue> fields;

    MapValueImpl(MapDef def) {
        super(def);
        fields = new TreeMap<String, FieldValue>();
    }

    /* DPL */
    private MapValueImpl() {
        super(null);
        fields = null;
    }

    /*
     * Public api methods from Object and FieldValue
     */

    @Override
    public MapValueImpl clone() {
        MapValueImpl map = new MapValueImpl(getDefinition());
        for (Map.Entry<String, FieldValue> entry : fields.entrySet()) {
            map.put(entry.getKey(), entry.getValue().clone());
        }
        return map;
    }

    @Override
    public int hashCode() {
        int code = size();
        for (Map.Entry<String, FieldValue> entry : fields.entrySet()) {
            code += entry.getKey().hashCode() + entry.getValue().hashCode();
        }
        return code;
    }

    @Override
    public boolean equals(Object other) {

        if (other instanceof MapValueImpl) {
            MapValueImpl otherValue = (MapValueImpl) other;
            /* maybe avoid some work */
            if (this == otherValue) {
                return true;
            }
            /*
             * detailed comparison
             */
            if (size() == otherValue.size() &&
                getElementDef().equals(otherValue.getElementDef()) &&
                getDefinition().equals(otherValue.getDefinition())) {

                for (Map.Entry<String, FieldValue> entry : fields.entrySet()) {
                    if (!entry.getValue().
                        equals(otherValue.get(entry.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * FieldDef must match.
     *
     * Compare field values in order of keys.  The algorithm relies on the fact
     * that fields is a SortedMap (TreeMap).  Return as soon as there is a
     * difference. If this object has a field the other does not, return > 0.
     * If this object is missing a field the other has, return < 0.  Compare
     * both keys and values, keys first.
     */
    @Override
    public int compareTo(FieldValue other) {

        if (other instanceof MapValueImpl) {
            MapValueImpl otherImpl = (MapValueImpl) other;

            if (!getDefinition().equals(otherImpl.getDefinition())) {
                throw new IllegalArgumentException
                    ("Cannot compare MapValues with different definitions");
            }

            /* this relies on the maps being sorted */
            assert fields instanceof TreeMap;
            assert otherImpl.fields instanceof TreeMap;

            Iterator<String> keyIter = fields.keySet().iterator();
            Iterator<String> otherIter = otherImpl.fields.keySet().iterator();

            while (keyIter.hasNext() && otherIter.hasNext()) {
                String key = keyIter.next();
                String otherKey = otherIter.next();
                int keyCompare = key.compareTo(otherKey);
                if (keyCompare != 0) {
                    return keyCompare;
                }
                /*
                 * Keys are equal, values must exist.
                 */
                FieldValue val = fields.get(key);
                FieldValue otherVal = otherImpl.fields.get(key);
                int valCompare = val.compareTo(otherVal);
                if (valCompare != 0) {
                    return valCompare;
                }
            }

            /*
             * The object with more keys is greater, otherwise they are equal.
             */
            if (keyIter.hasNext()) {
                return 1;
            } else if (otherIter.hasNext()) {
                return -1;
            }
            return 0;
        }
        throw new ClassCastException
            ("Object is not a MapValue");
    }

    @Override
    public FieldDef.Type getType() {
        return FieldDef.Type.MAP;
    }

    @Override
    public boolean isMap() {
        return true;
    }

    @Override
    public MapValue asMap() {
        return this;
    }

    /*
     * Public api methods from MapValue
     */

    @Override
    public MapDefImpl getDefinition() {
        return (MapDefImpl)fieldDef;
    }

    @Override
    public int size() {
        return fields.size();
    }

    @Override
    public Map<String, FieldValue> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public FieldValue remove(String fieldName) {
        return fields.remove(fieldName);
    }

    @Override
    public FieldValueImpl get(String fieldName) {
        return (FieldValueImpl)fields.get(fieldName);
    }

    @Override
    public MapValue put(String name, int value) {
        putScalar(name, getElementDef().createInteger(value));
        return this;
    }

    @Override
    public MapValue put(String name, long value) {
        putScalar(name, getElementDef().createLong(value));
        return this;
    }

    @Override
    public MapValue put(String name, String value) {
        putScalar(name, getElementDef().createString(value));
        return this;
    }

    @Override
    public MapValue put(String name, double value) {
        putScalar(name, getElementDef().createDouble(value));
        return this;
    }

    @Override
    public MapValue put(String name, float value) {
        putScalar(name, getElementDef().createFloat(value));
        return this;
    }

    @Override
    public MapValue putNumber(String name, int value) {
        putScalar(name, getElementDef().createNumber(value));
        return this;
    }


    @Override
    public MapValue putNumber(String name, long value) {
        putScalar(name, getElementDef().createNumber(value));
        return this;
    }


    @Override
    public MapValue putNumber(String name, float value) {
        putScalar(name, getElementDef().createNumber(value));
        return this;
    }

    @Override
    public MapValue putNumber(String name, double value) {
        putScalar(name, getElementDef().createNumber(value));
        return this;
    }

    @Override
    public MapValue putNumber(String name, BigDecimal value) {
        putScalar(name, getElementDef().createNumber(value));
        return this;
    }

    @Override
    public MapValue put(String name, boolean value) {
        putScalar(name, getElementDef().createBoolean(value));
        return this;
    }

    @Override
    public MapValue put(String name, byte[] value) {
        putScalar(name, getElementDef().createBinary(value));
        return this;
    }

    @Override
    public MapValue putJsonNull(String name) {
        if (!getElementDef().isJson()) {
            throw new IllegalArgumentException(
                "Cannot insert a JSON null into a non-JSON map");
        }
        fields.put(name, NullJsonValueImpl.getInstance());
        return this;
    }

    /*
     * This is only used internally for queries involving indexes on map keys
     */
    MapValue putNull(String name) {
        fields.put(name, NullValueImpl.getInstance());
        return this;
    }

    @Override
    public MapValue putFixed(String name, byte[] value) {
        putScalar(name, getElementDef().createFixedBinary(value));
        return this;
    }

    @Override
    public MapValue putEnum(String name, String value) {
        putScalar(name, getElementDef().createEnum(value));
        return this;
    }

    @Override
    public MapValue put(String name, Timestamp value) {
        putScalar(name, getElementDef().createTimestamp(value));
        return this;
    }

    @Override
    public MapValue put(String fieldName, FieldValue value) {
        value = validate(value, getElementDef());
        fields.put(fieldName, value);
        return this;
    }

    @Override
    public RecordValueImpl putRecord(String fieldName) {
        RecordValue val = getElementDef().createRecord();
        fields.put(fieldName, val);
        return (RecordValueImpl) val;
    }

    @Override
    public MapValueImpl putMap(String fieldName) {
        MapValue val = getElementDef().createMap();
        fields.put(fieldName, val);
        return (MapValueImpl) val;
    }

    @Override
    public ArrayValueImpl putArray(String fieldName) {
        ArrayValue val = getElementDef().createArray();
        fields.put(fieldName, val);
        return (ArrayValueImpl) val;
    }

    @Override
    public MapValue putJson(String fieldName,
                            String jsonInput) {
        Reader reader = new StringReader(jsonInput);
        try {
            return putJson(fieldName, reader);
        } finally {
            try { reader.close(); } catch (IOException ioe) {}
        }
    }

    @Override
    public MapValue putJson(String fieldName,
                            Reader jsonReader) {
        put(fieldName, JsonDefImpl.createFromReader(jsonReader));
        return this;
    }

    /*
     * ComplexValueImpl internal api methods
     */

    @Override
    public Map<String, FieldValue> getMap() {
        return fields;
    }

    /**
     * Add JSON fields to the map.
     */
    @Override
    public void addJsonFields(
        JsonParser jp,
        String currentFieldName,
        boolean exact,
        boolean addMissingFields) {

        try {
            FieldDef element = getElementDef();

            JsonToken t = jp.getCurrentToken();
            if (t == null) {
                /* JSON is empty */
                return;
            }
            assert(t == JsonToken.START_OBJECT);

            while (jp.nextToken() != JsonToken.END_OBJECT) {

                String fieldname = jp.getCurrentName();
                JsonToken token = jp.nextToken();

                /*
                 * A json null is valid only if the element type of
                 * the map is JSON.
                 */
                if (token == JsonToken.VALUE_NULL && !element.isJson()) {
                    throw new IllegalArgumentException
                        ("Invalid null value in JSON input for field "
                         + fieldname);
                }

                switch (element.getType()) {
                case INTEGER:
                    put(fieldname, jp.getIntValue());
                    break;
                case LONG:
                    put(fieldname, jp.getLongValue());
                    break;
                case DOUBLE:
                    put(fieldname, jp.getDoubleValue());
                    break;
                case FLOAT:
                    put(fieldname, jp.getFloatValue());
                    break;
                case NUMBER:
                    putNumber(fieldname, jp.getDecimalValue());
                    break;
                case STRING:
                    put(fieldname, jp.getText());
                    break;
                case BINARY:
                    put(fieldname, jp.getBinaryValue());
                    break;
                case FIXED_BINARY:
                    putFixed(fieldname, jp.getBinaryValue());
                    break;
                case BOOLEAN:
                    put(fieldname, jp.getBooleanValue());
                    break;
                case TIMESTAMP:
                    put(fieldname,
                        element.asTimestamp().fromString(jp.getText()));
                    break;
                case ARRAY:
                    /*
                     * current token is '[', then array elements
                     * TODO: need to have a full-on switch for adding
                     * array elements of the right type.
                     */
                    ArrayValueImpl array = putArray(fieldname);
                    array.addJsonFields(jp, null, exact, addMissingFields);
                    break;
                case MAP:
                    MapValueImpl map = putMap(fieldname);
                    map.addJsonFields(jp, null, exact, addMissingFields);
                    break;
                case RECORD:
                    RecordValueImpl record = putRecord(fieldname);
                    record.addJsonFields(jp, null, exact, addMissingFields);
                    break;
                case ENUM:
                    putEnum(fieldname, jp.getText());
                    break;
                case JSON:
                case ANY_JSON_ATOMIC:
                    put(fieldname, JsonDefImpl.createFromJson(jp, false));
                    break;
                case ANY:
                    throw new IllegalArgumentException(
                        "MAP(ANY) not suported yet");
                case ANY_ATOMIC:
                    throw new IllegalArgumentException(
                        "MAP(ANY_ATOMIC) not suported yet");
                case ANY_RECORD:
                    throw new IllegalStateException(
                        "A map type cannot have ANY_RECORD as its " +
                        "element type");
                case EMPTY:
                    throw new IllegalStateException(
                        "A map type cannot have EMPTY as its element type");
                }
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException
                (("Failed to parse JSON input: " + ioe.getMessage()), ioe);
        } catch (RuntimeException re) {
            throw new IllegalArgumentException
                (("Failed to parse JSON input: " + re.toString()), re);
        }
    }

    /*
     * FieldValueImpl internal api methods
     */

    @Override
    public FieldValueImpl getFieldValue(String fieldName) {
        return (FieldValueImpl)fields.get(fieldName);
    }

    /**
     * Map is represented as ObjectNode.  Jackson does not have a MapNode
     */
    @Override
    public JsonNode toJsonNode() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, FieldValue> entry : fields.entrySet()) {
            node.put(entry.getKey(),
                     ((FieldValueImpl)entry.getValue()).toJsonNode());
        }
        return node;
    }

    @Override
    public void toStringBuilder(StringBuilder sb) {
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, FieldValue> entry : fields.entrySet()) {
            String key = entry.getKey();
            FieldValueImpl val = (FieldValueImpl)entry.getValue();
            if (val != null) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('\"');
                sb.append(key);
                sb.append('\"');
                sb.append(':');
                val.toStringBuilder(sb);
                i++;
            }
        }
        sb.append('}');
    }

    @SuppressWarnings("unchecked")
    static MapValueImpl fromJavaObjectValue(FieldDef def, Object o) {

        Map<String, Object> javaMap = (Map<String, Object>) o;

        MapValue map = def.createMap();

        for (Map.Entry<String, Object> entry : javaMap.entrySet()) {
            String key = entry.getKey().toString();
            map.put(
                key,
                FieldValueImpl.fromJavaObjectValue(
                    map.getDefinition().getElement(),
                    entry.getValue()));
        }
        return (MapValueImpl)map;
    }

    @Override
    FieldValueImpl findFieldValue(
        ListIterator<String> fieldPath,
        int arrayIndex) {

        assert fieldPath.hasNext();

        String next = fieldPath.next();

        /*
         * This method is called when validating index keys.  If the key
         * is the target, just validate that there's a single entry
         * and return the value based on that key.
         */
        if (MapDefImpl.isMapKeyTag(next)) {

            /*
             * this call path comes from IndexImpl.findMapKey().
             */
            if (size() != 1) {

                /*
                 * If the map has 2 entries and the other is a "[]" entry
                 * then this map is in an index  with both key and value
                 * defined.
                 */
                if (size() != 2 || !fields.containsKey(ANONYMOUS)) {
                    throw new IllegalArgumentException
                        ("Index keys for maps can contain only one entry");
                }
            }

            /*
             * Never return the key matching "[]" as it's not useful, so
             * if it is present, skip it.
             */
            Iterator<String> keyIter = fields.keySet().iterator();
            while (keyIter.hasNext()) {
                String keyVal = keyIter.next();
                if (MapDefImpl.isMapValueTag(keyVal)) {
                    continue;
                }
                return FieldDefImpl.stringDef.createString(keyVal);
            }
            return null;
        }

        FieldValueImpl fv = get(next);
        if (fv == null || !fieldPath.hasNext()) {
            return fv;
        }
        return fv.findFieldValue(fieldPath, arrayIndex);
    }

    /**
     * This method is used to find fields mostly during index serialization,
     * and has to handle the special field components "_key" and "[]"
     * and return the correct information for each.
     *
     * It is used both in serializing index keys extracted on the server side
     * and in serializing index keys used in the client for index scan operations.
     * In the former case mapKey will always be non-null because it's extracting
     * an entry for each map entry. In addition the mapKey will never be "_key"
     * or "[]" because they are not valid map key strings.
     *
     * In the latter case the map may have null entries for the "_key" index
     * component and it may have entries using the "[]" key for value
     * index components.
     *
     * If the index field component is "_key" then a StringValue based on
     * the mapKey is returned.
     *
     * If the index field component is "[]" then mapKey may be null, in
     * which case "[]" is used as the key.
     */
    @Override
    FieldValueImpl findFieldValue(
        ListIterator<String> fieldPath,
        String mapKey) {

        assert fieldPath.hasNext();

        String next = fieldPath.next();

        /*
         * If the field is the _key, return a string value based on the map key.
         */
        if (MapDefImpl.isMapKeyTag(next)) {
            return FieldDefImpl.stringDef.createString(mapKey);
        }

        FieldValueImpl fv = null;

        /*
         * If the key is null and the component is [], use "[]" as the
         * key.  This will happen in IndexKeys and FieldRanges, never when
         * extracting index keys on the server.
         */
        if (MapDefImpl.isMapValueTag(next)) {
            if (mapKey == null) {
                fv = get(next);
            } else {
                fv = get(mapKey);
            }
        } else {
            fv = get(next);
        }

        /*
         * Null values are never serializable, they are equivalent to null.
         */
        if (fv != null && fv.isNull()) {
            fv = null;
        }

        if (fv == null || !fieldPath.hasNext()) {
            return fv;
        }

        return fv.findFieldValue(fieldPath, mapKey);
    }

    /*
     * local methods
     */

    /**
     * Clears the map.
     */
    void clearMap() {
        fields.clear();
    }

    /*
     * Cheap validation for scalars. If types match, nothing to do; if not,
     * do the more expensive work in validate().
     */
    private MapValueImpl putScalar(String fieldName, FieldValue value) {
        if (getDefinition().getType() != value.getType()) {
            value = validate(value, getElementDef());
        }
        fields.put(fieldName, value);
        return this;
    }

    FieldDefImpl getElementDef() {
        return getDefinition().getElement();
    }

    public Map<String, FieldValue> getFieldsInternal() {
        return fields;
    }

    public Set<String> getFieldNames() {
        return fields.keySet();
    }

    /**
     * This version is used internally for index deserialization.  Enums are
     * stored as an integer index into the enumeration values in indexes.
     */
    MapValue putEnum(String name, int index) {
        fields.put(name, ((EnumDefImpl)getElementDef()).createEnum(index));
        return this;
    }
}
