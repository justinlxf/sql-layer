/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.sql.aisddl;

import com.foundationdb.ais.model.AkibanInformationSchema;

import com.foundationdb.server.api.DDLFunctions;
import com.foundationdb.server.error.DropSchemaNotAllowedException;
import com.foundationdb.server.error.DuplicateSchemaException;
import com.foundationdb.server.error.NoSuchSchemaException;
import com.foundationdb.server.service.session.Session;

import com.foundationdb.sql.parser.CreateSchemaNode;
import com.foundationdb.sql.parser.DropSchemaNode;
import com.foundationdb.sql.parser.ExistenceCheck;
import com.foundationdb.sql.parser.StatementType;
import com.foundationdb.qp.operator.QueryContext;

public class SchemaDDL {
    private SchemaDDL () {
    }
    
    public static void createSchema (AkibanInformationSchema ais,
                                   String defaultSchemaName,
                                   CreateSchemaNode createSchema,
                                   QueryContext context)
    {
        final String schemaName = createSchema.getSchemaName();
        ExistenceCheck condition = createSchema.getExistenceCheck();
        
        if (ais.getSchema(schemaName) != null)
            switch(condition)
            {
                case IF_NOT_EXISTS:
                    // schema already exists. does nothing
                    if (context != null)
                        context.warnClient(new DuplicateSchemaException(schemaName));
                    return;
                case NO_CONDITION:
                    throw new DuplicateSchemaException (schemaName);
                default:
                    throw new IllegalStateException("Unexpected condition in CREATE SCHEMA: " + condition);
            }

        // If you get to this point, the schema name isn't being used by any user or group table
        // therefore is a valid "new" schema. 
        // TODO: update the AIS to store the new schema. 
    }
    
    public static void dropSchema (DDLFunctions ddlFunctions,
            Session session,
            DropSchemaNode dropSchema,
            QueryContext context)
    {
        AkibanInformationSchema ais = ddlFunctions.getAIS(session);
        final String schemaName = dropSchema.getSchemaName();
        ExistenceCheck condition = dropSchema.getExistenceCheck();
        
        if (ais.getSchema(schemaName) != null)
        {
            // 1 == RESTRICT, meaning no drop if the schema isn't empty 
            if (dropSchema.getDropBehavior() == StatementType.DROP_RESTRICT ||
                    dropSchema.getDropBehavior() == StatementType.DROP_DEFAULT)
                throw new DropSchemaNotAllowedException (schemaName);
            // If the schema isn't used by any existing tables, it has effectively 
            // been dropped, so the drop "succeeds".
            else if (dropSchema.getDropBehavior() == StatementType.DROP_CASCADE) 
                ddlFunctions.dropSchema(session, schemaName);       
        }
        else
            switch(condition)
            {
                case IF_EXISTS:
                    // schema doesn't exists. does nothing
                    if (context != null)
                        context.warnClient(new NoSuchSchemaException(schemaName));
                    return;
                case NO_CONDITION:
                    throw new NoSuchSchemaException(schemaName);
                default:
                    throw new UnsupportedOperationException("Unexpected condition in DROP SCHEMA: " + condition);
            }
        
    }
    
}
