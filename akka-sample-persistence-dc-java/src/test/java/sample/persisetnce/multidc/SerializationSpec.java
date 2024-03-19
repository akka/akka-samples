/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.persisetnce.multidc;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.junit.ClassRule;
import org.junit.Test;
import sample.persistence.res.counter.ThumbsUpCounter;

public class SerializationSpec {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testSerialization() {
        testKit.serializationTestKit().verifySerialization(new ThumbsUpCounter.GaveThumbsUp("id1"), false);
        testKit.serializationTestKit().verifySerialization(new ThumbsUpCounter.State(), false);
        testKit.serializationTestKit().verifySerialization(new ThumbsUpCounter.GetCount("r1", testKit.<Long>createTestProbe().ref()), false);
        testKit.serializationTestKit().verifySerialization(new ThumbsUpCounter.GetUsers("r1", testKit.<ThumbsUpCounter.State>createTestProbe().ref()), false);
        testKit.serializationTestKit().verifySerialization(new ThumbsUpCounter.GiveThumbsUp("r1", "u1", testKit.<Long>createTestProbe().ref()), false);
    }

}
