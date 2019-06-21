/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.github.vmencik.akkanative;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

// see https://medium.com/graalvm/instant-netty-startup-using-graalvm-native-image-generation-ed6f14ff7692

// should no longer be needed, but https://github.com/vmencik/akka-graal-config/issues/4

@TargetClass(className = "akka.actor.LightArrayRevolverScheduler$")
final class Target_akka_actor_LightArrayRevolverScheduler$ {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClassName = "akka.actor.LightArrayRevolverScheduler$TaskHolder", name = "task")
    public long akka$actor$LightArrayRevolverScheduler$$taskOffset;
}

public class AkkaSubstitutions {
}
