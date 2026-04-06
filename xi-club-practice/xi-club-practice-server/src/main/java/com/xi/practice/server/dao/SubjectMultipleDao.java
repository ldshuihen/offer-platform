package com.xi.practice.server.dao;

import com.xi.practice.server.entity.po.SubjectMultiplePO;

import java.util.List;

public interface SubjectMultipleDao {

    /**
     * 查询题目
     */
    List<SubjectMultiplePO> selectBySubjectId(Long subjectId);

}