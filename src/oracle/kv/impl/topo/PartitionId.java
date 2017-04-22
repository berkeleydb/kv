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

package oracle.kv.impl.topo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.sleepycat.persist.model.Persistent;

@Persistent
public class PartitionId extends ResourceId
    implements Comparable<PartitionId> {

    public static PartitionId NULL_ID = new PartitionId(-1);

    public PartitionId(int partitionId) {
        super();
        this.partitionId = partitionId;
    }

    private static final long serialVersionUID = 1L;

    private int partitionId;

    @SuppressWarnings("unused")
    private PartitionId() {

    }

    public boolean isNull() {
        return partitionId == NULL_ID.partitionId;
    }

    /**
     * FastExternalizable constructor.  Must call superclass constructor first
     * to read common elements.
     */
    public PartitionId(DataInput in, short serialVersion)
        throws IOException {

        super(in, serialVersion);
        partitionId = in.readInt();
    }

    /**
     * FastExternalizable writer.  Must call superclass method first to write
     * common elements.
     */
    @Override
    public void writeFastExternal(DataOutput out, short serialVersion)
        throws IOException {

        super.writeFastExternal(out, serialVersion);
        out.writeInt(partitionId);
    }

    @Override
    public ResourceType getType() {
        return ResourceType.PARTITION;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public String getPartitionName() {
        return "p" + partitionId;
    }

    public static boolean isPartitionName(String name) {
        if (name.startsWith("p")) {
            try {
                Integer.parseInt(name.substring(1));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return getType() + "-" + partitionId;
    }

    /* (non-Javadoc)
     * @see oracle.kv.impl.admin.ResourceId#getComponent(oracle.kv.impl.topo.Topology)
     */
    @Override
    public Partition getComponent(Topology topology) {
       return topology.get(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PartitionId other = (PartitionId) obj;
        if (partitionId != other.partitionId) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + partitionId;
        return result;
    }

    @Override
    public int compareTo(PartitionId other) {
        return this.partitionId - other.partitionId;
    }
}
