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

import java.io.Serializable;

/**
 * The base class for all table changes. A sequence of changes can be
 * applied to a TableMetadata instance, via {@link TableMetadata#apply}
 * to make it more current.
 * <p>
 * Each TableChange represents a logical change entry in a logical log with
 * changes being applied in sequence via {@link TableMetadata#apply} to modify
 * the table metadata and bring it up to date.
 */
abstract class TableChange implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int sequenceNumber;

    protected TableChange(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    int getSequenceNumber() {
        return sequenceNumber;
    }

    abstract boolean apply(TableMetadata md);
}
