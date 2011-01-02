/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.entity

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

import org.moqui.entity.EntityListIterator
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.entity.EntityException

class EntityListIteratorImpl implements EntityListIterator {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityListIteratorImpl.class)

    protected EntityFacadeImpl efi
    protected Connection con
    protected ResultSet rs

    protected EntityDefinition entityDefinition
    protected Set<String> fieldsSelected

    /** This is needed to determine if the ResultSet is empty as cheaply as possible. */
    protected boolean haveMadeValue = false

    protected boolean closed = false

    EntityListIteratorImpl(Connection con, ResultSet rs, EntityDefinition entityDefinition, Set<String> fieldsSelected, EntityFacadeImpl efi) {
        this.efi = efi
        this.con = con
        this.rs = rs
    }

    @Override
    void close() {
        if (this.closed) {
            logger.warn("EntityListIterator for entity [${this.entityDefinition.getEntityName()}] is already closed, not closing again")
        } else {
            if (rs) {
                try {
                    rs.close()
                } catch (SQLException e) {
                    throw new EntityException("Could not close ResultSet in EntityListIterator", e)
                }
            }
            if (con) {
                try {
                    con.close()
                } catch (SQLException e) {
                    throw new EntityException("Could not close Connection in EntityListIterator", e)
                }
            }
            this.closed = true
        }
    }

    @Override
    void afterLast() {
        try {
            rs.afterLast()
        } catch (SQLException e) {
            throw new EntityException("Error moving EntityListIterator to afterLast", e)
        }
    }

    @Override
    void beforeFirst() {
        try {
            rs.beforeFirst()
        } catch (SQLException e) {
            throw new EntityException("Error moving EntityListIterator to beforeFirst", e)
        }
    }

    @Override
    boolean last() {
        try {
            return rs.last()
        } catch (SQLException e) {
            throw new EntityException("Error moving EntityListIterator to last", e)
        }
    }

    @Override
    boolean first() {
        try {
            return rs.first()
        } catch (SQLException e) {
            throw new EntityException("Error moving EntityListIterator to first", e)
        }
    }

    @Override
    EntityValue currentEntityValue() {
        EntityValueImpl newEntityValue = new EntityValueImpl(this.entityDefinition, this.efi)
        int j = 1
        for (String fieldName in this.fieldsSelected) {
            EntityQueryBuilder.getResultSetValue(rs, j, entityDefinition.getFieldNode(fieldName), newEntityValue, this.efi)
            j++
        }
        this.haveMadeValue = true
        return newEntityValue
    }

    @Override
    int currentIndex() {
        try {
            return rs.getRow()
        } catch (SQLException e) {
            throw new EntityException("Error getting current index", e)
        }
    }

    @Override
    boolean absolute(int rowNum) {
        try {
            return rs.absolute(rowNum)
        } catch (SQLException e) {
            throw new EntityException("Error going to absolute row number [${rowNum}]", e)
        }
    }

    @Override
    boolean relative(int rows) {
        try {
            return rs.relative(rows)
        } catch (SQLException e) {
            throw new EntityException("Error moving relative rows [${rows}]", e)
        }
    }

    @Override
    boolean hasNext() {
        try {
            if (rs.isLast() || rs.isAfterLast()) {
                return false
            } else {
                // if not in the first or beforeFirst positions and haven't made any values yet, the result set is empty
                return !(!haveMadeValue && !rs.isBeforeFirst() && !rs.isFirst())
            }
        } catch (SQLException e) {
            throw new EntityException("Error while checking to see there is a next result", e)
        }
    }

    @Override
    boolean hasPrevious() {
        try {
            if (rs.isFirst() || rs.isBeforeFirst()) {
                return false
            } else {
                // if not in the last or afterLast positions and we haven't made any values yet, the result set is empty
                return !(!haveMadeValue && !rs.isAfterLast() && !rs.isLast())
            }
        } catch (SQLException e) {
            throw new EntityException("Error while checking to see there is a previous result", e)
        }
    }

    @Override
    EntityValue next() {
        try {
            if (rs.next()) {
                return currentEntityValue()
            } else {
                return null
            }
        } catch (SQLException e) {
            throw new EntityException("Error getting next result", e)
        }
    }

    @Override
    int nextIndex() {
        return currentIndex() + 1
    }

    @Override
    EntityValue previous() {
        try {
            if (rs.previous()) {
                return currentEntityValue()
            } else {
                return null
            }
        } catch (SQLException e) {
            throw new EntityException("Error getting previous result", e)
        }
    }

    @Override
    int previousIndex() {
        return currentIndex() - 1
    }

    @Override
    void setFetchSize(int rows) {
        try {
            rs.setFetchSize(rows)
        } catch (SQLException e) {
            throw new EntityException("Error setting fetch size", e)
        }
    }

    @Override
    EntityList getCompleteList() {
        try {
            // move back to before first if we need to
            if (haveMadeValue && !rs.isBeforeFirst()) {
                rs.beforeFirst()
            }
            EntityList list = new EntityListImpl(this.efi)
            EntityValue value
            while ((value = this.next()) != null) {
                list.add(value)
            }
            return list
        } catch (SQLException e) {
            throw new EntityException("Error getting all results", e)
        }
    }

    @Override
    EntityList getPartialList(int start, int number) {
        try {
            EntityList list = new EntityListImpl(this.efi)
            if (number == 0) list

            // list is 1 based
            if (start == 0) start = 1

            // jump to start index, or just get the first result
            if (!this.absolute(start)) {
                // not that many results, get empty list
                return list
            }

            // get the first as the current one
            list.add(this.currentEntityValue())

            int numberSoFar = 1
            EntityValue nextValue
            while (number > numberSoFar && (nextValue = this.next()) != null) {
                list.add(nextValue)
                numberSoFar++
            }
            return list
        } catch (SQLException e) {
            throw new EntityException("Error getting partial results", e)
        }
    }

    @Override
    Iterator<EntityValue> iterator() {
        return this
    }

    @Override
    void remove() {
        // TODO: call EECAs
        // TODO: notify cache clear
        try {
            rs.deleteRow()
        } catch (SQLException e) {
            throw new EntityException("Error removing row", e)
        }
    }

    @Override
    void set(EntityValue e) {
        throw new IllegalArgumentException("EntityListIterator.set() not currently supported")
        // TODO implement this
        // TODO: call EECAs
        // TODO: notify cache clear
    }

    @Override
    void add(EntityValue e) {
        throw new IllegalArgumentException("EntityListIterator.add() not currently supported")
        // TODO implement this
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                this.close()
                logger.error("EntityListIterator not closed for entity [${entityDefinition.getEntityName()}], caught in finalize()")
            }
        } catch (Exception e) {
            logger.error("Error closing the SQLProcessor in finalize EntityListIterator", e);
        }
        super.finalize()
    }
}
