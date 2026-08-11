package com.salesforce.cantor.selector;

import java.io.IOException;

public interface Select {
    String query(CantorSelectRequest request) throws IOException;
}