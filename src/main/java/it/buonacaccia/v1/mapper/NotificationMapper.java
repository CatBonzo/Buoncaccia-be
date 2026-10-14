package it.buonacaccia.v1.mapper;

import java.util.List;

import it.buonacaccia.model.Notification;
import it.buonacaccia.v1.dto.NotificationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/** Conversione delle notifiche del registro (feature 004). */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    NotificationMapper INSTANCE = Mappers.getMapper(NotificationMapper.class);

    NotificationDTO toDto(Notification notification);

    List<NotificationDTO> toDto(List<Notification> notifications);
}
