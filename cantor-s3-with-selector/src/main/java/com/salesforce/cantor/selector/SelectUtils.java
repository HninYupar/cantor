package com.salesforce.cantor.selector;

import java.io.IOException;

public abstract class SelectUtils {
    public abstract String query(CantorSelectRequest request) throws IOException;
}


