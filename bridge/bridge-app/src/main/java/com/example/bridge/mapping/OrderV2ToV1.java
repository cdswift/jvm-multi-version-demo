package com.example.bridge.mapping;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

/**
 * Translates ACME 2.0 objects back to ACME 1.0. This direction loses data,
 * and every loss has to be declared here or the build fails:
 *
 * <ul>
 *   <li>{@code unmappedSourcePolicy = ERROR}: a 2.0 field with no 1.0
 *       equivalent must be listed in {@code ignoreUnmappedSourceProperties}.</li>
 *   <li>Enums: MapStruct always refuses to compile if a source constant
 *       (here {@code CANCELLED}) has no target. You must say what happens.</li>
 * </ul>
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface OrderV2ToV1 {

    OrderV2ToV1 INSTANCE = Mappers.getMapper(OrderV2ToV1.class);

    @BeanMapping(ignoreUnmappedSourceProperties = "channel") // 1.0 has nowhere to put it
    @Mapping(target = "amountCents", source = "amount", qualifiedByName = "dollarsToCents")
    @Mapping(target = "items", source = "lines")
    v1.com.acme.model.Order toV1(v2.com.acme.model.Order order);

    @Mapping(target = "qty", source = "quantity")
    v1.com.acme.model.Item toV1(v2.com.acme.model.LineItem line);

    @ValueMapping(source = "SHIPPED", target = "SENT")
    @ValueMapping(source = "CANCELLED", target = MappingConstants.THROW_EXCEPTION)
    v1.com.acme.model.Status toV1(v2.com.acme.model.Status status);

    @Named("dollarsToCents")
    default long dollarsToCents(BigDecimal dollars) {
        return dollars.movePointRight(2).longValueExact();
    }
}
