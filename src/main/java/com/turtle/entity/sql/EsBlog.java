package com.turtle.entity.sql;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * @author lijiayu
 * @date 2020/7/24
 * @description
 */
@Data
@Accessors(chain = true)
public class EsBlog {

    private Long id;

    private LocalDateTime updateTime;

    @Field(analyzer = "ik_max_word" ,type = FieldType.Text)
    private String content;

    private float score;
}
