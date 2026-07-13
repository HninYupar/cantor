package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.common.AbstractBaseSetsTest;

import java.io.IOException;

public class IntegrationSetsTest extends AbstractBaseSetsTest {
    private static Cantor cantor;

    public static void setCantor(Cantor client) {
        cantor = client;
    }

    @Override
    protected Cantor getCantor() throws IOException {
        return cantor;
    }
}