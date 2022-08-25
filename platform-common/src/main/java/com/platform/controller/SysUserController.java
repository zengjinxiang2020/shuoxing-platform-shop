package com.platform.controller;

import com.alibaba.fastjson.JSONObject;
import com.platform.annotation.SysLog;
import com.platform.entity.SysUserEntity;
import com.platform.service.SysUserRoleService;
import com.platform.service.SysUserService;
import com.platform.utils.*;
import com.platform.validator.Assert;
import com.platform.validator.ValidatorUtils;
import com.platform.validator.group.AddGroup;
import com.platform.validator.group.UpdateGroup;
import com.platform.vo.EchartsData;
import org.apache.commons.lang.ArrayUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 系统用户
 *
 * @author lipengjun
 * @email 939961241@qq.com
 * @date 2016年10月31日 上午10:40:10
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController extends AbstractController {
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private SysUserRoleService sysUserRoleService;

    /**
     * 所有用户列表
     */
    @RequestMapping("/list")
    @RequiresPermissions("sys:user:list")
    public R list(@RequestParam Map<String, Object> params) {
        //只有超级管理员，才能查看所有管理员列表
        if (getUserId() != Constant.SUPER_ADMIN) {
            params.put("createUserId", getUserId());
        }

        //查询列表数据
        Query query = new Query(params);
        List<SysUserEntity> userList = sysUserService.queryList(query);
        int total = sysUserService.queryTotal(query);

        PageUtils pageUtil = new PageUtils(userList, total, query.getLimit(), query.getPage());
        System.out.println("*****");
        return R.ok().put("page", pageUtil);
    }

    /**
     * 获取登录的用户信息
     */
    @RequestMapping("/info")
    public R info() {
        return R.ok().put("user", getUser());
    }

    /**
     * 修改登录用户密码
     */
    @SysLog("修改密码")
    @RequestMapping("/password")
    public R password(String password, String newPassword) {
        if(ResourceUtil.getConfigByName("sys.demo").equals("1")){
            throw new RRException("演示环境无法修改密码！");
        }
        Assert.isBlank(newPassword, "新密码不为能空");

        //sha256加密
        password = new Sha256Hash(password).toHex();
        //sha256加密
        newPassword = new Sha256Hash(newPassword).toHex();

        //更新密码
        int count = sysUserService.updatePassword(getUserId(), password, newPassword);
        if (count == 0) {
            return R.error("原密码不正确");
        }

        //退出
        ShiroUtils.logout();

        return R.ok();
    }

    /**
     * 用户信息
     */
    @RequestMapping("/info/{userId}")
    @RequiresPermissions("sys:user:info")
    public R info(@PathVariable("userId") Long userId) {
        SysUserEntity user = sysUserService.queryObject(userId);

        //获取用户所属的角色列表
        List<Long> roleIdList = sysUserRoleService.queryRoleIdList(userId);
        user.setRoleIdList(roleIdList);

        return R.ok().put("user", user);
    }

    /**
     * 保存用户
     */
    @SysLog("保存用户")
    @RequestMapping("/save")
    @RequiresPermissions("sys:user:save")
    public R save(@RequestBody SysUserEntity user) {
        ValidatorUtils.validateEntity(user, AddGroup.class);

        user.setCreateUserId(getUserId());
        sysUserService.save(user);

        return R.ok();
    }

    /**
     * 修改用户
     */
    @SysLog("修改用户")
    @RequestMapping("/update")
    @RequiresPermissions("sys:user:update")
    public R update(@RequestBody SysUserEntity user) {
        ValidatorUtils.validateEntity(user, UpdateGroup.class);

        user.setCreateUserId(getUserId());
        sysUserService.update(user);

        return R.ok();
    }

    /**
     * 删除用户
     */
    @SysLog("删除用户")
    @RequestMapping("/delete")
    @RequiresPermissions("sys:user:delete")
    public R delete(@RequestBody Long[] userIds) {
        if (ArrayUtils.contains(userIds, 1L)) {
            return R.error("系统管理员不能删除");
        }

        if (ArrayUtils.contains(userIds, getUserId())) {
            return R.error("当前用户不能删除");
        }

        sysUserService.deleteBatch(userIds);

        return R.ok();
    }

    /**
     * 统计各种总数
     */
    @SysLog("统计各种总数")
    @RequestMapping("/countTotalUser")
    @RequiresPermissions("sys:user:list")
    public R coutTotalUser() {
        Map<String,Object> map = new HashMap<>();
        //查询用户总数
        map.put("totalUser",sysUserService.countTotalUser());
        //查询评论总数
        map.put("totalComment",sysUserService.countComment());
        //查询总交易额
        map.put("totalPrice",sysUserService.countPrice());
        //统计总销量
        map.put("totalShopping",sysUserService.countShopping());
        return R.ok().put("map",map);
    }

    /**
     * 统计分类销售额
     */
    @SysLog("统计分类销售额")
    @RequestMapping("/price")
    @RequiresPermissions("sys:user:list")
    public R getShoppingEchartsData() {
        List<EchartsData> list = new ArrayList<>();
        List<Map<String, Object>> typePriceList = sysUserService.getTypePrice();
        Map<String, BigDecimal> typeMap = new HashMap<>();
        for(Map<String, Object> map : typePriceList){
            typeMap.put((String)map.get("name"),(BigDecimal) map.get("price"));
        }

        getPieData("分类总销售额",list,typeMap);
        getBarData("分类总销售额",list,typeMap);

        return R.ok().put("list",list);
    }

    /**
     * 统计分类销量
     */
    @SysLog("统计分类销量")
    @RequestMapping("/shopping")
    @RequiresPermissions("sys:user:list")
    public R getShopping() {
        List<EchartsData> list = new ArrayList<>();
        List<Map<String, Object>> typePriceList = sysUserService.getTypeCount();
        Map<String, BigDecimal> typeMap = new HashMap<>();
        for(Map<String, Object> map : typePriceList){
            typeMap.put((String)map.get("name"),new BigDecimal(Integer.parseInt(map.get("number").toString())));
        }

        getPieData("分类总销量",list,typeMap);
        getBarData("分类总销量",list,typeMap);

        return R.ok().put("list",list);
    }

    /**
     * 封装饼图数据
     * @param name  标题
     * @param pieList   封装完给前端显示的list
     * @param dataMap   传入的数据
     */
    private void getPieData(String name,List pieList,Map<String,BigDecimal> dataMap){
        EchartsData pieData = new EchartsData();
        Map<String, String> titleMap = new HashMap<>();
        titleMap.put("text",name);
        titleMap.put("text",name);
        titleMap.put("left","center");
        pieData.setTitle(titleMap);

        Map<String, Object> tooltipMap = new HashMap<>();
        tooltipMap.put("trigger","item");
        pieData.setTooltip(tooltipMap);

        Map<String, String> legendMap = new HashMap<>();
        legendMap.put("orient","vertical");
        legendMap.put("left","left");
        pieData.setLegend(legendMap);

        EchartsData.Series series = new EchartsData.Series();
        series.setName(name);
        series.setType("pie");
        series.setRadius("50%");
        List<Object> data = new ArrayList<>();
        for(String key : dataMap.keySet()){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name",key);
            jsonObject.put("value",dataMap.get(key));
            data.add(jsonObject);
//            data.add(new JSONObject().putOpt("name",key).putOpt("value",dataMap.get(key)));
        }
        series.setData(data);
        pieData.setSeries(Collections.singletonList(series));
        pieList.add(pieData);

    }

    /**
     * 封装柱状图数据
     * @param name  标题
     * @param pieList   封装完给前端显示的list
     * @param dataMap   传入的数据
     */
    private void getBarData(String name,List pieList,Map<String,BigDecimal> dataMap){
        EchartsData barData = new EchartsData();
        Map<String, String> titleMap = new HashMap<>();
        titleMap.put("text",name);
        barData.setTitle(titleMap);

        Map<String, Object> tooltipMap = new HashMap<>();
        tooltipMap.put("show",true);
        barData.setTooltip(tooltipMap);

        EchartsData.Series series = new EchartsData.Series();
        series.setName(name);
        series.setType("bar");
        List<Object> data = new ArrayList<>();
        List<Object> xAxisObj = new ArrayList<>();
        for(String key : dataMap.keySet()){
            data.add(dataMap.get(key));
            xAxisObj.add(key);
        }
        series.setData(data);
        Map<String, Object> xAxisMap = new HashMap<>();
        xAxisMap.put("data",xAxisObj);
        barData.setxAxis(xAxisMap);
        barData.setyAxis(new HashMap());



        barData.setSeries(Collections.singletonList(series));
        pieList.add(barData);

    }


}
