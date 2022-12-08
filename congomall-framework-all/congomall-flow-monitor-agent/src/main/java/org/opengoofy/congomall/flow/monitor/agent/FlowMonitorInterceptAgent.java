/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengoofy.congomall.flow.monitor.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.opengoofy.congomall.flow.monitor.agent.bytebuddy.*;
import org.opengoofy.congomall.flow.monitor.agent.context.FlowMonitorRuntimeContext;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * 微服务流量监控拦截插桩
 *
 * @author chen.ma
 * @github https://github.com/opengoofy
 */
public final class FlowMonitorInterceptAgent {
    
    /**
     * 微服务流量监控插桩
     *
     * @param agentArgs       agent 传递参数
     * @param instrumentation 待处理桩
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("this is an perform monitor agent.");
        FlowMonitorRuntimeContext.init();
        consumerOpenFeignHandleInstrument(instrumentation);
        provideWebMvcHandlerInstrument(instrumentation);
        xxlJobHandleInstrument(instrumentation);
        streamRocketMQConsumerHandleInstrument(instrumentation);
        streamRocketMQProvideHandleInstrument(instrumentation);
    }
    
    /**
     * 消费者 OpenFeign 插件进行插桩处理
     *
     * @param instrumentation 待处理桩
     */
    private static void consumerOpenFeignHandleInstrument(Instrumentation instrumentation) {
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("feign.Client"))
                .transform((builder, typeDescription, classLoader, module) -> {
                    builder = builder.visit(
                            Advice
                                    .to(FeignFlowInterceptor.class)
                                    .on(ElementMatchers.named("execute")));
                    return builder;
                })
                .installOn(instrumentation);
    }
    
    /**
     * 提供者 Spring MVC 进行插桩处理
     *
     * @param instrumentation 待处理桩
     */
    private static void provideWebMvcHandlerInstrument(Instrumentation instrumentation) {
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod"))
                .transform((builder, typeDescription, classLoader, module) -> {
                    builder = builder.method(ElementMatchers.named("invokeAndHandle"))
                            .intercept(MethodDelegation.to(SpringMvcInterceptor.class));
                    return builder;
                })
                .installOn(instrumentation);
    }
    
    /**
     * XXL-Job 任务执行统计
     *
     * @param instrumentation 待处理桩
     */
    private static void xxlJobHandleInstrument(Instrumentation instrumentation) {
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("com.xxl.job.core.handler.impl.MethodJobHandler"))
                .transform((builder, typeDescription, classLoader, module) -> {
                    builder = builder.method(ElementMatchers.named("execute"))
                            .intercept(MethodDelegation.to(XXLJobInterceptor.class));
                    return builder;
                })
                .installOn(instrumentation);
    }
    
    /**
     * SpringCloud Stream RocketMQ Consumer 消费执行统计
     *
     * @param instrumentation 待处理桩
     */
    private static void streamRocketMQConsumerHandleInstrument(Instrumentation instrumentation) {
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("org.springframework.messaging.handler.invocation.InvocableHandlerMethod"))
                .transform((builder, typeDescription, classLoader, module) -> {
                    builder = builder.visit(
                            Advice
                                    .to(StreamRocketMQConsumerInterceptor.class)
                                    .on(ElementMatchers.named("doInvoke")));
                    return builder;
                }).installOn(instrumentation);
    }
    
    /**
     * SpringCloud Stream RocketMQ Provider 生产执行统计
     *
     * @param instrumentation 待处理桩
     */
    private static void streamRocketMQProvideHandleInstrument(Instrumentation instrumentation) {
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("org.springframework.cloud.stream.messaging.DirectWithAttributesChannel"))
                .transform((builder, typeDescription, classLoader, module) -> {
                    builder = builder.method(ElementMatchers.named("doSend").and(ElementMatchers.isProtected()).and(takesArguments(2)))
                            .intercept(MethodDelegation.to(StreamRocketMQProviderInterceptor.class));
                    return builder;
                })
                .installOn(instrumentation);
    }
}
