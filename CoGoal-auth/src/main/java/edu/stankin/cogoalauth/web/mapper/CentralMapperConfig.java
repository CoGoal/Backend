package edu.stankin.cogoalauth.web.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared settings of all MapStruct mappers.
 * <p>
 * {@code unmappedTargetPolicy = ERROR}: a target field that is neither mapped nor explicitly ignored
 * fails the build, so a new DTO field cannot be silently left empty.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CentralMapperConfig {
}
