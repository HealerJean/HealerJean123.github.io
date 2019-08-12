package com.hlj.proj.service.flow.base.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hlj.proj.data.dao.mybatis.manager.flow.*;
import com.hlj.proj.data.pojo.flow.*;
import com.hlj.proj.dto.user.IdentityInfoDTO;
import com.hlj.proj.exception.BusinessException;
import com.hlj.proj.service.flow.service.enums.FlowAuditType;
import com.hlj.proj.service.spring.SpringContextHolder;
import com.hlj.proj.utils.JsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ClassName AuditorProcess
 * @Author TD
 * @Date 2019/6/12 15:15
 * @Description 审批流程
 */
@Data
@Slf4j
public class AuditorProcess {

    /**
     * 审批链条
     */
    private String instantsNo;
    /**
     * 审批人
     */
    private List<Auditor> auditors;
    /**
     * 审批执行步骤
     */
    private AtomicInteger auditSept;


    public static AuditorProcess of(Long auditRecordId) {
        AuditorProcess auditorProcess = new AuditorProcess();

        //查询审批记录
        ScfFlowAuditRecordManager scfFlowAuditRecordManager = SpringContextHolder.getBean(ScfFlowAuditRecordManager.class);
        ScfFlowAuditRecord auditRecord =scfFlowAuditRecordManager.findById(auditRecordId);
        //查询流程节点
        ScfFlowNodeManager scfFlowNodeManager = SpringContextHolder.getBean(ScfFlowNodeManager.class);
        ScfFlowNodeQuery scfFlowNodeQuery = new ScfFlowNodeQuery();
        scfFlowNodeQuery.setNodeCode(auditRecord.getNodeCode());
        ScfFlowNode scfFlowNode = scfFlowNodeManager.findByQueryContion(scfFlowNodeQuery);
        String nodeDetail = scfFlowNode.getNodeDetail();
        if (StringUtils.isBlank(nodeDetail)) {
            auditorProcess.setAuditors(new ArrayList<>(0));
        } else {
            ArrayList<Auditor> auditors =  JsonUtils.toObject(nodeDetail,new TypeReference<ArrayList<Auditor>>() { });
            auditorProcess.setAuditors(auditors);
        }
        auditorProcess.setAuditSept(new AtomicInteger(auditRecord.getAuditSept()));
        auditorProcess.setInstantsNo(auditRecord.getInstantsNo());
        return auditorProcess;
    }



    /**
     * 同意(true)/(false)拒绝当前审批
     */
    public void audit(AuditorResult auditorResult, IdentityInfoDTO identityInfo) {
        JpaTransactionManager transactionManager = SpringContextHolder.getBean(JpaTransactionManager.class);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            int i = auditSept.intValue();
            Auditor auditor = auditors.get(i - 1);
            ScfFlowAuditRecordManager scfFlowAuditRecordManager = SpringContextHolder.getBean(ScfFlowAuditRecordManager.class);
            ScfFlowAuditRecord auditRecord = scfFlowAuditRecordManager.findById(auditorResult.getAuditRecordId());
            //先判断是否成功
            Boolean auditSuccess = auditorResult.getAuditResult();
            if (auditSuccess) {
                auditor.setStatus(Result.StatusEnum.Success.getCode());
            } else {
                auditor.setStatus(Result.StatusEnum.Fail.getCode());
            }
            //保存 审核记录 数据
            auditRecord.setStatus(auditor.getStatus());
            auditRecord.setOptUser(identityInfo.getUserId());
            auditRecord.setOptUserName(identityInfo.getRealName());
            auditRecord.setOptTime(new Date());
            auditRecord.setAuditMessage(auditorResult.getAuditMessage());
            auditRecord.setRefFileIds(JsonUtils.toJsonString(auditorResult.getFileIds()));
            scfFlowAuditRecordManager.updateSelective(auditRecord);

            //将所有审核人计划变成完成
            FlowRefAuditorEventManager auditorEventManager =  SpringContextHolder.getBean(FlowRefAuditorEventManager.class);
            FlowRefAuditorEventQuery flowRefAuditorEventQuery = new FlowRefAuditorEventQuery();
            flowRefAuditorEventQuery.setRefFlowAuditRecordId(auditRecord.getId());
            List<FlowRefAuditorEvent> auditorEvents = auditorEventManager.queryList(flowRefAuditorEventQuery);
            auditorEvents.stream().map(item->{
                item.setStatus(Result.StatusEnum.Success.getCode());
                return item;
            });
            auditorEventManager.batchUpdate(auditorEvents);

            //保存审批日志
            saveAuditLog(auditRecord);


            String auditData = auditRecord.getAuditData();
            log.info("序列化出来的数据：{}", auditData);
            //当审批流程全部走完时，则触发流程的下一步
            auditSept.incrementAndGet();
            if (isSuccess() || isReject()) {
                Process process = ProcessDefinition.ofSuspendInstant(instantsNo);
                process.nextFlow(instantsNo, auditData, identityInfo);
            } else {
                //创建下一个审批记录
                createAuditRecord(auditorResult.getInstantsNo(), auditorResult.getNodeCode(), auditSept.intValue(), null);
            }
            transactionManager.commit(status);
        } catch (BusinessException e) {
            log.error("审批错误",e);
            transactionManager.rollback(status);
            throw e;
        }  catch (Exception e) {
            log.error("审批错误",e);
            transactionManager.rollback(status);
            throw new BusinessException("审批错误");
        }
    }

    /**
     * 保存审批日志
     * @param auditRecord
     */
    private void saveAuditLog(ScfFlowAuditRecord auditRecord) {
        FlowAuditRecordLog flowAuditRecordLog = new FlowAuditRecordLog();
        flowAuditRecordLog.setRefFlowAuditRecordId(auditRecord.getId());
        flowAuditRecordLog.setRefFileIds(auditRecord.getRefFileIds());
        flowAuditRecordLog.setInstantsNo(auditRecord.getInstantsNo());
        flowAuditRecordLog.setSept(auditRecord.getSept());
        flowAuditRecordLog.setFlowCode(auditRecord.getFlowCode());
        flowAuditRecordLog.setFlowName(auditRecord.getFlowName());
        flowAuditRecordLog.setNodeCode(auditRecord.getNodeCode());
        flowAuditRecordLog.setNodeName(auditRecord.getNodeName());
        flowAuditRecordLog.setAuditSept(auditRecord.getAuditSept());
        flowAuditRecordLog.setAuditData(auditRecord.getAuditData());
        flowAuditRecordLog.setStatus(auditRecord.getStatus());
        flowAuditRecordLog.setOptUser(auditRecord.getOptUser());
        flowAuditRecordLog.setOptUserName(auditRecord.getOptUserName());
        flowAuditRecordLog.setOptTime(auditRecord.getOptTime());
        flowAuditRecordLog.setAuditMessage(auditRecord.getAuditMessage());
        FlowAuditRecordLogManager flowAuditRecordLogManager = SpringContextHolder.getBean(FlowAuditRecordLogManager.class);
        flowAuditRecordLogManager.save(flowAuditRecordLog);
    }


    /**
     * 审核开始
     */
    public void initAudit(AuditorFlowNode auditorFlowNode, String instantsNo, String data) {
        createAuditRecord(instantsNo, auditorFlowNode.getNodeCode(), 1, data);
    }


    /**
     * 判断该审批是否成功并且完成
     * @return
     */
    public Boolean isSuccess() {
        int i = auditSept.intValue();
        if(i > auditors.size() ){
            return true;
        }
        //获取当前审批对象
        Auditor auditor = auditors.get(i - 1);
        return auditSept.intValue() == auditors.size() && Result.StatusEnum.Success.getCode().equals(auditor.getStatus());
    }

    /**
     * 判断该审批是否失败
     * @return
     */
    public Boolean isReject() {
        int i = auditSept.intValue();
        Auditor auditor = auditors.get(i - 1);
        return Result.StatusEnum.Fail.getCode().equals(auditor.getStatus());
    }


    /**
     * 创建审批记录
     */
    private void createAuditRecord(String instantsNo, String nodeCode, Integer auditSept, String data) {
        this.auditSept = new AtomicInteger(auditSept);

        ScfFlowRecordManager scfFlowRecordManager = SpringContextHolder.getBean(ScfFlowRecordManager.class);
        ScfFlowRecordQuery scfFlowRecordQuery = new ScfFlowRecordQuery();
        scfFlowRecordQuery.setInstantsNo(instantsNo);
        scfFlowRecordQuery.setStatus(Result.StatusEnum.Suspend.getCode());
        scfFlowRecordQuery.setNodeCode(nodeCode);
        ScfFlowRecord scfFlowRecord = scfFlowRecordManager.findByQueryContion(scfFlowRecordQuery);
        if (scfFlowRecord == null) {
            log.error("执行时找不到对应的流程记录，instantsNo：{}", instantsNo);
            throw new BusinessException("执行时找不到对应的流程记录");
        }


        ScfFlowAuditRecordManager scfFlowAuditRecordManager = SpringContextHolder.getBean(ScfFlowAuditRecordManager.class);
        ScfFlowNodeQuery scfFlowNodeQuery = new ScfFlowNodeQuery();
        scfFlowNodeQuery.setNodeCode(nodeCode);
        //创建第一个审核记录
        ScfFlowAuditRecord scfFlowAuditRecord = new ScfFlowAuditRecord();
        scfFlowAuditRecord.setInstantsNo(scfFlowRecord.getInstantsNo());
        scfFlowAuditRecord.setSept(scfFlowRecord.getSept());
        scfFlowAuditRecord.setFlowCode(scfFlowRecord.getFlowCode());
        scfFlowAuditRecord.setFlowName(scfFlowRecord.getFlowName());
        scfFlowAuditRecord.setNodeCode(scfFlowRecord.getNodeCode());
        scfFlowAuditRecord.setNodeName(scfFlowRecord.getNodeName());
        scfFlowAuditRecord.setAuditSept(auditSept);
        scfFlowAuditRecord.setStatus(Result.StatusEnum.Suspend.getCode());
        scfFlowAuditRecord.setAuditData(data);
        scfFlowAuditRecordManager.insertSelective(scfFlowAuditRecord);
        saveAuditLog(scfFlowAuditRecord);
        Auditor auditor = auditors.get(auditSept - 1);
        if (auditor != null) {
            List<FlowRefAuditorEvent> list = new ArrayList<>();
            List<Long> ids = auditor.getIds();
            List<Long> roles = auditor.getRoles();
            if (ids != null && !ids.isEmpty()) {
                for (int i = 0; i < ids.size(); i++) {
                    FlowRefAuditorEvent auditorEvent = new FlowRefAuditorEvent();
                    auditorEvent.setRefFlowAuditRecordId(scfFlowAuditRecord.getId());
                    auditorEvent.setAuditType(FlowAuditType.ID.getType());
                    auditorEvent.setAuditObject(ids.get(i));
                    auditorEvent.setStatus(Result.StatusEnum.Suspend.getCode());
                    list.add(auditorEvent);
                }
            }
            if (roles != null && !roles.isEmpty()) {
                for (int i = 0; i < roles.size(); i++) {
                    FlowRefAuditorEvent auditorEvent = new FlowRefAuditorEvent();
                    auditorEvent.setRefFlowAuditRecordId(scfFlowAuditRecord.getId());
                    auditorEvent.setAuditType(FlowAuditType.ROLE.getType());
                    auditorEvent.setAuditObject(roles.get(i));
                    auditorEvent.setStatus(Result.StatusEnum.Suspend.getCode());
                    list.add(auditorEvent);
                }
            }
            FlowRefAuditorEventManager flowRefAuditorEventManager = SpringContextHolder.getBean(FlowRefAuditorEventManager.class);
            flowRefAuditorEventManager.batchInsert(list);
        }
    }
}
