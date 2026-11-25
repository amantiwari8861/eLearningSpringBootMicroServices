package in.iamandroid.order.mapper;

import in.iamandroid.order.dto.EnrollmentResponse;
import in.iamandroid.order.entities.Enrollment;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EnrollmentMapper {

    EnrollmentResponse toResponse(Enrollment enrollment);
}
