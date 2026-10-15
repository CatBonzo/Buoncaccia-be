package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Refund;
import it.buonacaccia.service.Money;
import it.buonacaccia.v1.dto.RefundDTO;
import it.buonacaccia.v1.dto.RefundSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = Money.class)
public interface RefundMapper {

    RefundMapper INSTANCE = Mappers.getMapper(RefundMapper.class);

    @Mapping(target = "amount", expression = "java(Money.toEuro(refund.getAmountCents()))")
    RefundDTO toDto(Refund refund);

    List<RefundDTO> toDtoList(List<Refund> refunds);

    @Mapping(target = "amount", expression = "java(Money.toEuro(refund.getAmountCents()))")
    RefundSummaryDTO toSummary(Refund refund);
}
