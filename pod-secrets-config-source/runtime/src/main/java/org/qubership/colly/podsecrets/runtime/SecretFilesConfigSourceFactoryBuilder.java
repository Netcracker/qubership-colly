package org.qubership.colly.podsecrets.runtime;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * {@link ConfigBuilder} registered via {@code RunTimeConfigBuilderBuildItem} in the
 * deployment module. Wires the {@link SecretFilesConfigSourceFactory} into SmallRye
 * config with no action required from the consuming application.
 */
public class SecretFilesConfigSourceFactoryBuilder implements ConfigBuilder {

    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        return builder.withSources(new SecretFilesConfigSourceFactory());
    }

    @Override
    public int priority() {
        return SecretFilesConfigSource.PRIORITY;
    }
}
