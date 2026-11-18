package org.qubership.colly.podsecrets.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import org.qubership.colly.podsecrets.runtime.SecretFilesConfigSourceFactoryBuilder;

/**
 * Build-time processor of the pod-secrets config source extension. Registers the
 * {@link SecretFilesConfigSourceFactoryBuilder} as a runtime config builder so the
 * file-backed {@code ConfigSource} is wired into the application configuration
 * automatically, with no annotation or services file in the consuming application.
 */
class SecretFilesConfigSourceProcessor {

    private static final String FEATURE = "colly-pod-secrets";

    @BuildStep
    void feature(BuildProducer<FeatureBuildItem> feature) {
        feature.produce(new FeatureBuildItem(FEATURE));
    }

    @BuildStep
    void configSourceFactory(BuildProducer<RunTimeConfigBuilderBuildItem> runTimeConfigBuilder) {
        runTimeConfigBuilder.produce(new RunTimeConfigBuilderBuildItem(SecretFilesConfigSourceFactoryBuilder.class));
    }
}
