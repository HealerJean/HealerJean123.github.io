package com.hlj.proj.controller.flow;

import com.hlj.proj.common.ValidateGroup;
import com.hlj.proj.constants.CommonConstants;
import com.hlj.proj.dto.PageDTO;
import com.hlj.proj.dto.ResponseBean;
import com.hlj.proj.dto.user.IdentityInfoDTO;
import com.hlj.proj.exception.BusinessException;
import com.hlj.proj.service.flow.base.entity.AuditorResult;
import com.hlj.proj.service.flow.service.ScfFlowService;
import com.hlj.proj.service.flow.service.dto.AuditRecordDTO;
import com.hlj.proj.util.UserUtils;
import com.hlj.proj.utils.validate.ValidateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@Slf4j
@RequestMapping("api/flow")
public class FlowController  {

    @Autowired
    private ScfFlowService scfFlowService;

    /**
     * 审批
     * @return
     */
    @PostMapping("audit")
    public ResponseBean audits(@RequestBody AuditorResult auditorResult) {
        if (auditorResult == null) {
            throw new BusinessException("审批参数不全");
        }
        String validate = ValidateUtils.validate(auditorResult, ValidateGroup.Audit.class);
        if (!CommonConstants.COMMON_SUCCESS.equals(validate)) {
            log.error("审批-------审批-----参数不完整{}", validate);
            throw new BusinessException(validate);
        }
        IdentityInfoDTO authUser = UserUtils.getAuthUser();
        scfFlowService.audit(auditorResult, authUser);
        log.info("审批流程完毕：流程编号，{}，步骤：{}", auditorResult.getInstantsNo(), auditorResult.getSept());
        return  ResponseBean.buildSuccess();
    }
}
