//package com.salesforce.cantor.selector;
//
//import com.adobe.testing.s3mock.testng.S3Mock;
//import com.adobe.testing.s3mock.testng.S3MockListener;
//import com.amazonaws.services.s3.AmazonS3;
//import com.salesforce.cantor.Cantor;
//import com.salesforce.cantor.common.AbstractBaseEventsTest;
//import org.testng.annotations.Listeners;
//import org.testng.annotations.Test;
//
//import java.io.IOException;
//
//
//@Listeners(value = { S3MockListener.class })
//@Test(enabled = false)
//public class EventsOnS3withSelectorTest extends AbstractBaseEventsTest {
//
//    @Override
//    protected Cantor getCantor() throws IOException {
//        final AmazonS3 s3Client = S3Mock.getInstance().createS3Client("us-west-1");
//        if (!s3Client.doesBucketExistV2("default")) {
//            s3Client.createBucket("default");
//        }
//        return new CantorOnS3withSelector(s3Client, "default");
//    }
//}