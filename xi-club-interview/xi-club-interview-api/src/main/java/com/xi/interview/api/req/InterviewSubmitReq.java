package com.xi.interview.api.req;

import com.xi.interview.api.enums.EngineEnum;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;


@Getter
@Setter
public class InterviewSubmitReq implements Serializable {

    private String engine = EngineEnum.JI_CHI.name();
    //private String engine = EngineEnum.ALI_BL.name();

    private String interviewUrl;

    private List<Submit> questionList;

    @Data
    public static class Submit {

        private String labelName;

        private String subjectName;

        private String subjectAnswer;

        private String userAnswer;

        private Double userScore;

    }

}
