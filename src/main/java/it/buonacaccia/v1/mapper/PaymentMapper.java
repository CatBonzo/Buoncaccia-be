package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Payment;
import it.buonacaccia.service.Money;
import it.buonacaccia.v1.dto.PaymentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = Money.class)
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

    @Mapping(target = "amount", expression = "java(Money.toEuro(payment.getAmountCents()))")
    PaymentDTO toDto(Payment payment);

    List<PaymentDTO> toDtoList(List<Payment> payments);
}
