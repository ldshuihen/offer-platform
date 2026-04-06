package com.xi.interview.api.req;

import com.xi.interview.api.enums.EngineEnum;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;


@Getter
@Setter
public class StartReq implements Serializable {

    private String engine = EngineEnum.JI_CHI.name();
    //private String engine = EngineEnum.ALI_BL.name();

    private List<Key> questionList;

    @Data
    public static class Key {
        private String keyWord;
        private Long categoryId;
        private Long labelId;
    }

}
