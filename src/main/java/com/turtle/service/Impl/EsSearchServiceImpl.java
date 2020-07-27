package com.turtle.service.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.turtle.entity.sql.EsBlog;
import com.turtle.service.EsSearchService;
import io.netty.handler.codec.json.JsonObjectDecoder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author lijiayu
 * @date 2020/7/23
 * @description
 */
@Service
public class EsSearchServiceImpl implements EsSearchService {
    @Qualifier("restHighLevelClient")
    @Autowired
    private RestHighLevelClient client;


    @Override
    public boolean createIndex(String index) throws IOException {
        //1.创建索引请求
        CreateIndexRequest request = new CreateIndexRequest(index);
        //2.客户端执行请求IndicesClient,请求后获得相应
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        return response.isAcknowledged();
    }


    @Override
    public boolean isExit(String index) throws IOException {
        GetIndexRequest request = new GetIndexRequest(index);
        boolean exists = client.indices().exists(request,RequestOptions.DEFAULT);
        return exists;
    }


    @Override
    public boolean delete(String index) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(index);
        AcknowledgedResponse delete = client.indices().delete(request,RequestOptions.DEFAULT);
        return delete.isAcknowledged();
    }


    @Override
    public boolean addDocument(String index, String id, Object object) throws IOException {
        IndexRequest request = new IndexRequest(index);
        //规则 一般的文档形如 put /index/_doc/1
        request.id(id);//如果不设置id的话会自动分配id
        request.timeout("1s");//设置超时时间
        //将我们的数据放入请求Json中
        request.source(JSON.toJSONString(object), XContentType.JSON);
        //客户端发送请求,获取相应的结果
        IndexResponse response = client.index(request,RequestOptions.DEFAULT);
        return response.getShardInfo().getSuccessful()>0?true:false;
    }


    @Override
    public boolean isdocuexit(String index, String id) throws IOException {
        GetRequest getRequest = new GetRequest(index,id);
        //不获取返回的_source上下文
        getRequest.fetchSourceContext(new FetchSourceContext(false));
        getRequest.storedFields("_none_");
        return client.exists(getRequest,RequestOptions.DEFAULT);
    }


    @Override
    public String getDoucumment(String index, String id) throws IOException {
        GetRequest getRequest = new GetRequest(index, id);
        GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
        return response.getSourceAsString();
    }


    @Override
    public boolean updateDocument(Object object, String index, String id) throws IOException {
        UpdateRequest request = new UpdateRequest(index, id);
        request.timeout("1s");

        request.doc(JSONObject.toJSONString(object),XContentType.JSON);
        UpdateResponse update = client.update(request, RequestOptions.DEFAULT);
        return update.getShardInfo().getSuccessful() > 0;
    }


    @Override
    public boolean deleteDocument(String index, String id) throws IOException{
        DeleteRequest request = new DeleteRequest(index,id);
        request.timeout("1s");
        DeleteResponse deleteResponse = client.delete(request,RequestOptions.DEFAULT);
        return deleteResponse.getShardInfo().getSuccessful() > 0;
    }


    @Override
    public boolean addmoredocument(ArrayList<Object> list, String index, String id) throws IOException{
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("1s");
        //批量处理请求
        for (int i=0;i<list.size();i++){
            bulkRequest.add(
                    new IndexRequest(index)
                            .id(id)
                            .source(JSON.toJSONString(list.get(i)),XContentType.JSON));
        }
        BulkResponse responses = client.bulk(bulkRequest,RequestOptions.DEFAULT);
        //是否失败 false-没有失败
        return !responses.hasFailures();
    }


    @Override
    public List<EsBlog> termQuery(String index, TreeMap<String, Object> content, int size, int page, boolean ishigh) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //构建查询条件
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        System.out.println(content.firstKey());
        //查询条件 (termQuery存在bug，查询不到结果，用matchPhraseQuery代替)
        MatchPhraseQueryBuilder matchPhraseQueryBuilder = QueryBuilders.matchPhraseQuery(content.firstKey(), content.get(content.firstKey()));
        sourceBuilder.query(matchPhraseQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        //获取多少条数据
        sourceBuilder.size(size);
        sourceBuilder.from((page-1)*size);
        //排序
        sourceBuilder.sort("_score",SortOrder.DESC);
        sourceBuilder.sort("id",SortOrder.ASC);

//        //从哪个id后开始
//        if(sortValues!=null && sortValues.length>0){
//            sourceBuilder.searchAfter(sortValues);
//        }
        //是否要将查询的结果中将搜索的关键词高亮
        if (ishigh){
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            //设置高亮的属性
            highlightBuilder.field(content.firstKey());
            //也可以自定义高亮的样式,这里我使用的是默认的方式
            sourceBuilder.highlighter(highlightBuilder);
        }
        //将查询条件放入需要查询中
        searchRequest.source(sourceBuilder);

        //获取相应的数据
        SearchResponse searchResponse = client.search(searchRequest,RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<EsBlog> result = new ArrayList<>();
        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            //获取高亮的信息
            HighlightField property = highlightFields.get(content.firstKey());
            //查询的元素数据(没有高亮)
            EsBlog esBlog = JSONObject.parseObject(searchHit.getSourceAsString(), EsBlog.class).setScore(searchHit.getScore());
            if (ishigh){
                if (property!=null){
                    Text[] fragments = property.fragments();
                    StringBuilder nTitle = new StringBuilder();
                    for (Text text:fragments){
                        nTitle.append(text);
                    }
                    esBlog.setContent(nTitle.toString());
                }
            }
            result.add(esBlog);
        }
        return result;
    }


    @Override
    public List<EsBlog> matchQuery(String index, TreeMap<String, Object> content, int size, int page, boolean ishigh) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //构建查询条件
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //查询条件
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(content.firstKey(),content.get(content.firstKey()));
//        matchQueryBuilder.analyzer("ik_smart");
        sourceBuilder.query(matchQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        sourceBuilder.sort("_score",SortOrder.DESC);
        sourceBuilder.sort("id",SortOrder.ASC);

        //获取多少条数据
        sourceBuilder.size(size);
        sourceBuilder.from((page-1)*size);
//        //从哪个id后开始 适用滚动查询
//        if(sortValues!=null && sortValues.length>0){
//            sourceBuilder.searchAfter(sortValues);
//        }
        //是否要高亮
        if (ishigh){
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            //设置高亮的属性
            highlightBuilder.field(content.firstKey());
            //也可以自定义高亮的样式,这里我使用的是默认的方式
            sourceBuilder.highlighter(highlightBuilder);
        }
        //将查询条件放入需要查询中
        searchRequest.source(sourceBuilder);

        //获取相应的数据
        SearchResponse searchResponse = client.search(searchRequest,RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<EsBlog> result = new ArrayList<>();
        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            //获取高亮的信息
            HighlightField property = highlightFields.get(content.firstKey());
            //查询的元素数据(没有高亮)
            String source = searchHit.getSourceAsString();
            EsBlog esBlog = JSONObject.parseObject(source, EsBlog.class).setScore(searchHit.getScore());
            if (ishigh){
                if (property!=null){
                    Text[] fragments = property.fragments();
                    StringBuilder nTitle = new StringBuilder();
                    for (Text text:fragments){
                        nTitle.append(text);
                    }
                    esBlog.setContent(nTitle.toString());
                }
            }
            result.add(esBlog);
        }
        return result;
    }



    @Override
    public List<Map<String,Object>> boolmustQuery(String index, TreeMap<String, Object> content, int size, int page) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //构建查询条件
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        Set keys = content.keySet();
        for (Object key:keys){
            //将要查询的条件加入
            boolQueryBuilder.must(QueryBuilders.termQuery((String) key,content.get(key)));
        }
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        //获取多少条数据
        sourceBuilder.size(size);
        //从第几行开始
        sourceBuilder.from((page-1)*size);

        //将查询条件放入需要查询中
        searchRequest.source(sourceBuilder);

        //获取相应的数据
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<Map<String,Object>> result = new ArrayList<>();

        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            result.add(searchHit.getSourceAsMap());
        }
        return result;
    }

}


