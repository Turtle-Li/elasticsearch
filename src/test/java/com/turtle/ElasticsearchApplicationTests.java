package com.turtle;

import com.turtle.entity.sql.EsBlog;
import com.turtle.service.EsSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SpringBootTest
class ElasticsearchApplicationTests {

    @Autowired
    EsSearchService esSearchService;

    @Test
    void contextLoads() throws IOException {
//        System.out.println(esSearchService.createIndex("blog"));
//        boolean b = esSearchService.addDocument("blog", "3", new EsBlog().setId(3L)
//                .setContent("根据上面的返回信息，集群的 unassigned_shards 为 2。"));
//        boolean b = esSearchService.deleteDocument("blog", "1");
        TreeMap<String,Object> map = new TreeMap<>();
        map.put("content","中华人名");
        List<EsBlog> b = esSearchService.matchQuery("blog", map, 5,new Object[]{},true);
        System.out.println(b);
    }

}
