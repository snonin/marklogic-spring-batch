package com.marklogic.spring.batch.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.repository.dao.NoSuchObjectException;

import com.marklogic.spring.batch.bind.JobExecutionAdapter;
import com.marklogic.spring.batch.bind.JobInstanceAdapter;

@XmlRootElement(name = "mlJobInstance", namespace=MarkLogicSpringBatch.JOB_NAMESPACE)
@XmlType(namespace=MarkLogicSpringBatch.JOB_NAMESPACE)
public class MarkLogicJobInstance {
	
	private JobInstance jobInstance;
	private List<JobExecution> jobExecutions = new ArrayList<JobExecution>();
	private String jobKey;
	private Date createDateTime;
	
	public MarkLogicJobInstance() {
		
	}
	
	public MarkLogicJobInstance(JobInstance jobInstance) {
		this.jobInstance = jobInstance;
	}
	
	@XmlJavaTypeAdapter(JobInstanceAdapter.class)
	@XmlElement(namespace=MarkLogicSpringBatch.JOB_NAMESPACE)
	public JobInstance getJobInstance() {
		return jobInstance;
	}
	
	public void setJobInstance(JobInstance jobInstance) {
		this.jobInstance = jobInstance;
	}
	
	@XmlJavaTypeAdapter(JobExecutionAdapter.class)
	@XmlElementWrapper( name="jobExecutions", namespace=MarkLogicSpringBatch.JOB_NAMESPACE )
	@XmlElement(name = "jobExecution", namespace=MarkLogicSpringBatch.JOB_NAMESPACE)
	public List<JobExecution> getJobExecutions() {
		return jobExecutions;
	}
	
	public void setJobExecutions(List<JobExecution> jobExecutions) {
		this.jobExecutions = jobExecutions;
	}
	
	public String getJobKey() {
		return jobKey;
	}
	
	public void setJobKey(String jobKey) {
		this.jobKey = jobKey;
	}
	
	public void addJobExecution(JobExecution jobExecution) {
		jobExecutions.add(jobExecution);
	}
	
	public void updateJobExecution(JobExecution jobExecution) {
		for (JobExecution je : jobExecutions) {
			if (je.getId().equals(jobExecution.getId())) {
				jobExecutions.remove(je);
			} else {
				throw new NoSuchObjectException("Invalid JobExecution, ID " + jobExecution.getId() + " not found.");
			}
			jobExecutions.add(jobExecution);
		}
	}

	public Date getCreateDateTime() {
		return createDateTime;
	}

	public void setCreatedDateTime(Date createdDateTime) {
		this.createDateTime = createdDateTime;
	}
}
