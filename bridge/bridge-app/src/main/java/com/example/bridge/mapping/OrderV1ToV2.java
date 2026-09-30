package com.example.bridge.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

/**
 * Translates ACME 1.0 objects to ACME 2.0 objects.
 *
 * <p>MapStruct generates {@code OrderV1ToV2Impl} from this interface at
 * compile time (see target/generated-sources/annotations). Only the
 * differences between the versions are written here. Same-named properties
 * ({@code id}, {@code status}, {@code customer}, {@code sku}) are mapped
 * automatically, including the nested v1 Customer class to the v2 Customer
 * record.
 *
 * <p>{@code unmappedTargetPolicy = ERROR}: if a future 2.x adds a field that
 * nothing here fills in, the build fails and names the field.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderV1ToV2 {

    OrderV1ToV2 INSTANCE = Mappers.getMapper(OrderV1ToV2.class);

    @Mapping(target = "amount", source = "amountCents", qualifiedByName = "centsToDollars")
    @Mapping(target = "lines", source = "items")
    @Mapping(target = "channel", constant = "LEGACY")
    v2.com.acme.model.Order toV2(v1.com.acme.model.Order order);

    /** Used for every element when mapping items -> lines. */
    @Mapping(target = "quantity", source = "qty")
    v2.com.acme.model.LineItem toV2(v1.com.acme.model.Item item);

    @ValueMapping(source = "SENT", target = "SHIPPED")
    v2.com.acme.model.Status toV2(v1.com.acme.model.Status status);

    @Named("centsToDollars")
    default BigDecimal centsToDollars(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
