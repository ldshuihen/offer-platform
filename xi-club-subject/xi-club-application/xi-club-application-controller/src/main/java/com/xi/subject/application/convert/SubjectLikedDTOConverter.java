package com.xi.subject.application.convert;

import com.xi.subject.application.dto.SubjectLikedDTO;
import com.xi.subject.domain.entity.SubjectLikedBO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 题目点赞数据传输对象（DTO）与业务对象（BO）转换接口
 * <p>
 * 负责 SubjectLikedDTO（应用层数据传输对象）和 SubjectLikedBO（领域层业务对象）之间的字段映射，
 * 基于 MapStruct 框架实现编译期自动生成转换代码，无运行时反射开销，保证转换性能。
 * </p>
 *
 * @author jingdianjichi
 * @since 2024-01-07 23:08:45
 */
@Mapper
public interface SubjectLikedDTOConverter {

    /**
     * 转换器单例实例（非 Spring 容器管理时使用）
     * <p>
     * 通过 MapStruct 内置的 Mappers 工厂获取接口实现类实例，适用于非 Spring 环境；
     * 若需交由 Spring 管理，可修改 @Mapper 注解添加 componentModel = "spring"。
     * </p>
     */
    SubjectLikedDTOConverter INSTANCE = Mappers.getMapper(SubjectLikedDTOConverter.class);

    /**
     * 将题目点赞 DTO 转换为 BO
     * <p>
     * 自动映射同名字段，若存在字段名/类型差异，可通过 @Mapping 注解自定义映射规则。
     * </p>
     *
     * @param subjectLikedDTO 题目点赞数据传输对象（应用层入参/出参），不可为 null
     * @return 题目点赞业务对象（领域层业务逻辑处理使用），返回 null 当且仅当入参为 null
     */
    SubjectLikedBO convertDTOToBO(SubjectLikedDTO subjectLikedDTO);

}