/**
 * Copyright (c) 2010 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the MIT/X11 license. The text of the license can be
 * found at http://www.opensource.org/licenses/mit-license.php.
 */

package org.extensiblecatalog.ncip.v2.service;

import org.apache.log4j.Logger;

/**
 * Note: This is in fact inherited from the NCIP version 1 VisibleItemIdentifierType, but
 * as the VisibleItemIdentifier element was renamed ItemIdentifier with version 2,
 * that is the name used for this scheme.
 */
public class Version2ItemIdentifierType extends ItemIdentifierType {

    private static final Logger LOG = Logger.getLogger(Version2ItemIdentifierType.class);

    public static final String VERSION_2_ITEM_IDENTIFIER_TYPE = "Schema";

    public static final Version2ItemIdentifierType ACCESSION_NUMBER
            = new Version2ItemIdentifierType(VERSION_2_ITEM_IDENTIFIER_TYPE, "Accession Number");
    public static final Version2ItemIdentifierType BARCODE
            = new Version2ItemIdentifierType(VERSION_2_ITEM_IDENTIFIER_TYPE, "Barcode");
    public static final Version2ItemIdentifierType UUID
            = new Version2ItemIdentifierType(VERSION_2_ITEM_IDENTIFIER_TYPE, "UUID");

    public static void loadAll() {
        LOG.debug("Loading Version1ItemIdentifierType.");
        // Do nothing - merely invoking this method forces the creation of the instances defined above.
    }

    public Version2ItemIdentifierType(String scheme, String value) {
        super(scheme, value);
    }

}
