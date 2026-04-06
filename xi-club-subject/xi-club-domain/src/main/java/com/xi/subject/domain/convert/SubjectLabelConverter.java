package com.xi.subject.domain.convert;

import com.xi.subject.domain.entity.SubjectLabelBO;
import com.xi.subject.infra.basic.entity.SubjectLabel;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;


@Mapper
public interface SubjectLabelConverter {

    SubjectLabelConverter INSTANCE = Mappers.getMapper(SubjectLabelConverter.class);

    SubjectLabel convertBoToLabel(SubjectLabelBO subjectLabelBO);

    List<SubjectLabelBO> convertLabelToBoList(List<SubjectLabel> subjectLabelList);
}
