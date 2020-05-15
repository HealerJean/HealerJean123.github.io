package com.healerjean.proj.controller;

import com.healerjean.proj.common.dto.ResponseBean;
import com.healerjean.proj.dto.ScheduleJobDTO;
import com.healerjean.proj.dao.mapper.ScheduleJobMapper;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author HealerJean
 * @version 1.0v
 * @ClassName DemoController
 * @date 2019/6/13  20:01.
 * @Description
 */
@ApiResponses(value = {
        @ApiResponse(code = 200, message = "访问正常"),
        @ApiResponse(code = 301, message = "逻辑错误"),
        @ApiResponse(code = 500, message = "系统错误"),
        @ApiResponse(code = 401, message = "未认证"),
        @ApiResponse(code = 403, message = "禁止访问"),
        @ApiResponse(code = 404, message = "url错误")
})
@Api(description = "demo控制器")
@Controller
@RequestMapping("hlj/demo")
@Slf4j
public class DemoController {

    @Resource
    private ScheduleJobMapper scheduleJobMapper;

    @ApiOperation(notes = "get",
            value = "get",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE,
            response = ScheduleJobDTO.class)
    @GetMapping("get")
    @ResponseBody
    public ResponseBean get( ) {
        return ResponseBean.buildSuccess(scheduleJobMapper.selectById(1L));
    }

}
