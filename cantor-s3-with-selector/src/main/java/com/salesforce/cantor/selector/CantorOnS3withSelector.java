package com.salesforce.cantor.selector;

import com.amazonaws.services.s3.AmazonS3;
import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.Events;
import com.salesforce.cantor.Objects;
import com.salesforce.cantor.Sets;

import java.io.IOException;

/**
 * This implementation is designed to only use a single s3 bucket.
 */
public class CantorOnS3withSelector implements Cantor {
    private final Objects objects;
    private final Events events;

    public CantorOnS3withSelector(final AmazonS3 s3Client, final String bucketName) throws IOException {
//        final Select select = new LocalSelect(s3Client);
        final Select select = new S3Select(s3Client);
        this.objects = new ObjectsOnS3withSelector(s3Client, bucketName);
        this.events = new EventsOnS3withSelector(s3Client, bucketName, select);
    }

    @Override
    public Objects objects() {
        return this.objects;
    }

    @Override
    public Sets sets() {
        throw new UnsupportedOperationException("Sets are not implemented on S3");
    }

    @Override
    public Events events() {
        return this.events;
    }
}
