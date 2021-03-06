package cn.blog.service.impl;

import cn.blog.bo.BlogBo;
import cn.blog.bo.TagsAndBlog;
import cn.blog.common.Const;
import cn.blog.common.ResponseCode;
import cn.blog.common.ServerResponse;
import cn.blog.dao.BlogMapper;
import cn.blog.dao.TagMapper;
import cn.blog.pojo.Blog;
import cn.blog.pojo.Tag;
import cn.blog.service.IBlogService;
import cn.blog.util.DateCalUtils;
import cn.blog.util.DateTimeUtil;
import cn.blog.util.PropertiesUtil;
import cn.blog.vo.BlogVo;
import cn.blog.vo.IndexVo;
import cn.blog.vo.TagVo;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.mysql.jdbc.StringUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.schema.Server;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @Description: 博客模块业务逻辑处理
 * Created by Jann Lee on 2018/1/20  0:51.
 */
@Slf4j
@Transactional
@Service("iBlogService")
public class BlogServiceImpl implements IBlogService {

    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private TagMapper tagMapper;

    /**
     * @Description:添加或修改（高复用） Created by Jann Lee on 2018/1/18  18:54.
     */
    //todo 更新时候日期转换
    @Override
    public ServerResponse<BlogVo> saveOrUpdate(Blog  blog,String tagIds) {
        int rowCount = 0;
        if (blog == null) {
            return ServerResponse.createByErrorCodeAndMessage(ResponseCode.NULL_ARGUMENT.getCode(), ResponseCode.NULL_ARGUMENT.getDesc());
        } else if (blog.getBlogId() != null) {
            //如果有id就更新
            //todo 校验管理员是否登录
            rowCount = blogMapper.updateByPrimaryKeySelective(blog);
            //删除所有标签
            tagMapper.deleteBlogTags(blog.getBlogId());

        } else {
            //没有id就添加
            //校验参数
            if (StringUtils.isNullOrEmpty(blog.getTitle()) || StringUtils.isNullOrEmpty(blog.getContent())) {
                return ServerResponse.createByErrorCodeAndMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
            }
            if (blog.getCategoryId() == null) {
                blog.setCategoryId(1);
            }
            if(blog.getAuthor()==null){
                blog.setAuthor("Jann Lee");
            }
            log.info(blog.toString());

            rowCount = blogMapper.insertSelective(blog);
        }

        //todo  处理标签
        String[] ids = null;
        //插入这些标签
        if(rowCount>0&&tagIds!=null&&tagIds.trim().length()>0){
            rowCount=0;
            ids = tagIds.trim().split(" ");
            List<Integer> idList = Lists.newArrayList();
            for(String str:ids){
                idList.add(Integer.parseInt(str));
            }
            rowCount = blogMapper.addTagsToBlog(blog.getBlogId(),idList);
        }

        //插入成功返回Vo
        if (rowCount > 0) {
            BlogBo blogBo = blogMapper.selectBoById(blog.getBlogId());
            if (blogBo != null) {
                return ServerResponse.createBySuccess(changeBoToVo(blogBo,null,true));
            }
        }
        return ServerResponse.createByErrorMessage("修改/新增失败！");
    }


    /**
     * 复用的博客查询列表
     *
     * @param code       博客类型码（0 public ,1.private ,2 recommenmed）
     * @param title      博客标题
     * @param tagId      标签id
     * @param categoryId 分类id
     * @param pageNum    页数
     * @param pageSize   每页显示数量
     * @return ServerResponse<PageInfo>
     */
    @Override
    public ServerResponse<PageInfo> listByCodeTitleTagCategory(Integer code, String title,String orderBy,

                                                               Integer tagId, Integer categoryId,Boolean isCalc, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        if(isCalc==null){
            isCalc=true;
        }
        //排序处理
        if(org.apache.commons.lang3.StringUtils.isNotBlank(orderBy)){
            //如果包含处理结果 就进行处理
            if(Const.BlogListOrderBy.VIEWCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.SHARECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.LIKECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.COMMENTCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.CREATETIME_ASC_DESC.contains(orderBy)
                    ){
                String [] orderByArray = orderBy.split("_");
                PageHelper.orderBy(orderByArray[0]+" "+orderByArray[1]);
            }
        }
        List<BlogBo> blogBoList = blogMapper.selectByCodeTitleTagCategory(code, title, tagId, categoryId);
        PageInfo pageInfo = new PageInfo(blogBoList);
        List<BlogVo> blogVoList = Lists.newArrayList();
        for (BlogBo blogBo : blogBoList) {
            blogVoList.add(changeBoToVo(blogBo,null,isCalc));
        }
        pageInfo.setList(blogVoList);


        return ServerResponse.createBySuccess(pageInfo);
    }

     //给博客添加标签
    @Override
    public ServerResponse addTagToBlog(Integer blogId, Integer tagId) {
        if (blogId == null || tagId == null) {
            return ServerResponse.createByErrorCodeAndMessage(ResponseCode.NULL_ARGUMENT.getCode()
                    , ResponseCode.NULL_ARGUMENT.getDesc());
        }
        //检验是否存在
        Blog blog = blogMapper.selectTagsByblogId(blogId);
        Tag tag = tagMapper.selectByPrimaryKey(tagId);
        TagsAndBlog tagsAndBlog = blogMapper.selectTagAndBlog(blogId, tagId);
        if (blog == null) {
            return ServerResponse.createByErrorMessage("该博客不存在！");
        } else if (tag == null) {
            return ServerResponse.createByErrorMessage("该标签不存在！");
        } else if (tagsAndBlog != null) {
            return ServerResponse.createByErrorMessage("标签已经被添加过，无需重复添加!");
        }

        int result = blogMapper.addTagToBlog(blogId, tagId);
        String newTag = tag.getTagName();

        StringBuilder tags = new StringBuilder(""+blog.getTags());
        if(tags.length()==0){
            tags.append(newTag);
        }else{
            tags.append("_"+newTag);
        }
        blog.setTags(tags.toString());
        //todo 插入数据库
        int rowCont = blogMapper.updateByPrimaryKeySelective(blog);
        if (result > 0&&rowCont>0) {
            return ServerResponse.createBySuccessMessage("添加成功！");
        }
        return ServerResponse.createByErrorMessage("添加失败！");
    }

    @Override
    public PageInfo findBlogVoPageInfo(Integer code,Integer categoryId,Integer tagId,
                                     String orderBy,String title,Integer pageNum,Integer pageSize){
        //排序处理
        if(org.apache.commons.lang3.StringUtils.isNotBlank(orderBy)){
            //如果包含处理结果 就进行处理
            if(Const.BlogListOrderBy.VIEWCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.SHARECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.LIKECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.COMMENTCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.CREATETIME_ASC_DESC.contains(orderBy)
                    ){
                String [] orderByArray = orderBy.split("_");
                PageHelper.orderBy(orderByArray[0]+" "+orderByArray[1]);
            }
        }else{
            PageHelper.orderBy("createTime"+" "+"desc");
        }
        if(pageNum==null||pageSize==null){
            PageHelper.startPage(1,Const.IndexConst.BLOG_NUM);
        }
        PageHelper.startPage(pageNum,pageSize);

        List<BlogBo> blogBoList = blogMapper.selectByCodeTitleTagCategory(code, title, tagId, categoryId);
        PageInfo pageInfo = new PageInfo(blogBoList);
        List<BlogVo> blogVoList = Lists.newArrayList();
        for (BlogBo blogBo : blogBoList) {
            blogVoList.add(changeBoToVo(blogBo,null,true));
        }
        pageInfo.setList(blogVoList);
        return pageInfo;
    }
    @Override
    public  List<BlogVo> findBlogVoList(Integer code,Integer categoryId,Integer tagId,
                                     String orderBy,String title,Integer pageNum,Integer pageSize){
        if(pageNum==null||pageSize==null){
            PageHelper.startPage(1,Const.IndexConst.BLOG_NUM);
        }
        PageHelper.startPage(pageNum,pageSize);
        //排序处理
        if(org.apache.commons.lang3.StringUtils.isNotBlank(orderBy)){
            //如果包含处理结果 就进行处理
            if(Const.BlogListOrderBy.VIEWCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.SHARECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.LIKECOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.COMMENTCOUNT_ASC_DESC.contains(orderBy)||
                    Const.BlogListOrderBy.CREATETIME_ASC_DESC.contains(orderBy)
                    ){
                String [] orderByArray = orderBy.split("_");
                PageHelper.orderBy(orderByArray[0]+" "+orderByArray[1]);
            }
        }else{
            PageHelper.orderBy("createTime"+" "+"desc");
        }
        log.info("这是pageInfo"+PageHelper.getOrderBy());
        List<BlogBo> blogBoList = blogMapper.selectByCodeTitleTagCategory(code, title, tagId, categoryId);
        List<BlogVo> blogVoList = Lists.newArrayList();
        for (BlogBo blogBo : blogBoList) {
            blogVoList.add(changeBoToVo(blogBo,null,false));
        }
        return blogVoList;
    }



    @Override
    public BlogVo descVo(Integer blogId) {
        if(blogId==null){
            return null;
        }
        BlogBo blogBo =  blogMapper.selectBoByIdWithBlobsNoSummary(blogId);
        BlogVo blogVo = changeBoToVoWithTags(blogBo,DateTimeUtil.STANDARD_FORMAT,false);
        return blogVo;
    }

    @Override
    public boolean isExists(Integer blogId) {
        int rowCount = blogMapper.selectCount(blogId);
        if(rowCount==0){
            return false;
        }
        return true;
    }

    /**
     * 猜你喜欢 根据该博客的标签 或者分类查询
     * @param blogId
     * @return
     */
    @Override
    public List<BlogVo> guessYouLike(Integer blogId) {
        //先根据标签 如果查询结果为空再根据categoryId来查询
        List<BlogBo> blogBoList=null;
        PageHelper.startPage(1,5);
        blogBoList = blogMapper.selectTheSameTagByBlogId(blogId);

        List<BlogVo> bLogVoList = Lists.newArrayList();
        if(blogBoList==null||blogBoList.size()<1){
            //查询同类的
            PageHelper.startPage(1,5);
            blogBoList = blogMapper.selectSameCategoryByBlogId(blogId);

        }else if(blogBoList==null||blogBoList.size()<1){
            //同类的没有在查公开的
            PageHelper.startPage(1,5);
            PageHelper.orderBy("viewCount"+" "+"desc");
            blogBoList = blogMapper.selectByCodeTitleTagCategory(1,null,null,null);
        }
        for(BlogBo blogBo:blogBoList){
            bLogVoList.add(changeBoToVo(blogBo,null,false));
        }
        return bLogVoList;
    }

    @Override
    public  BlogVo  findLast(Integer blogId) {

        BlogBo last = blogMapper.selectLastById(blogId);
        return  changeBoToVo(last,null,false);

    }
    @Override
    public  BlogVo  findNext(Integer blogId) {

        BlogBo next = blogMapper.selectNextById(blogId);
        return  changeBoToVo(next,null,false);

    }

    @Override
    public boolean addLike(Integer blogId) {
        int rowCount = blogMapper.addLike(blogId);
        if(rowCount>0){
            return true;
        }
        return false;
    }

    @Override
    public boolean cancelLike(Integer blogId) {
        int rowCount = blogMapper.cancelLike(blogId);
        if(rowCount>0){
            return true;
        }
        return false;
    }

    @Override
    public boolean addViewCount(Integer blogId) {
        int rowCount = blogMapper.addViewCount(blogId);
        if(rowCount>0){
            return true;
        }
        return false;
    }

    @Override
    public ServerResponse deleteBlog(Integer blogId) {
        int result = 0;
        //todo 进行校验处理
         result = tagMapper.deleteBlogTags(blogId);
         result =  blogMapper.deleteByPrimaryKey(blogId);
         if(result==0){
             return ServerResponse.createByErrorMessage("删除失败！");
         }

        return ServerResponse.createBySuccess();
    }

    private BlogVo changeBoToVo(BlogBo blogBo,String regex,boolean isCalc) {
        if (blogBo == null) {
            return null;
        }
        if(regex==null||regex.trim().length()==0||regex.length()==0){
            regex=DateTimeUtil.DATE_FORMAT;
        }
        BlogVo blogVo = new BlogVo();
        BeanUtils.copyProperties(blogBo, blogVo);
        if(blogBo.getCreateTime()!=null||blogBo.getUpdateTime()!=null){
            blogVo.setUpdateTimeStr(DateTimeUtil.dateToStr(blogBo.getUpdateTime()));
            if(isCalc){
                blogVo.setCreateTimeStr(DateCalUtils.format(blogBo.getCreateTime())+" ("+DateTimeUtil.dateToStr(blogBo.getCreateTime(),regex)+")");
            }else{
                blogVo.setCreateTimeStr(DateTimeUtil.dateToStr(blogBo.getCreateTime(),regex));

            }
        }
        blogVo.setImgHost(PropertiesUtil.getProperty("cdn.image.prefix"));
        blogVo.setCategoryName(blogBo.getCategoryName());

        return blogVo;
    }


    private BlogVo changeBoToVoWithTags(BlogBo blogBo,String regex,boolean isCalc) {
        if (blogBo == null) {
            return null;
        }
        if(regex==null||regex.trim().length()==0||regex.length()==0){
            regex=DateTimeUtil.DATE_FORMAT;
        }
        BlogVo blogVo = new BlogVo();
        BeanUtils.copyProperties(blogBo, blogVo);
        if(blogBo.getCreateTime()!=null||blogBo.getUpdateTime()!=null){
            blogVo.setUpdateTimeStr(DateTimeUtil.dateToStr(blogBo.getUpdateTime()));
            if(isCalc){
                blogVo.setCreateTimeStr(DateCalUtils.format(blogBo.getCreateTime())+" ("+DateTimeUtil.dateToStr(blogBo.getCreateTime(),regex)+")");
            }else{
                blogVo.setCreateTimeStr(DateTimeUtil.dateToStr(blogBo.getCreateTime(),regex));

            }
        }
        List<Tag> tagList = tagMapper.selectTagsOfBlog(blogBo.getBlogId());
        blogVo.setImgHost(PropertiesUtil.getProperty("cdn.image.prefix"));
        blogVo.setCategoryName(blogBo.getCategoryName());
        blogVo.setTagsList(tagList);
        return blogVo;
    }

}
