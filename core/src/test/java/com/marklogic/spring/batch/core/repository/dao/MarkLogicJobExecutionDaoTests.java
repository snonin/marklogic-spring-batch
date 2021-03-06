package com.marklogic.spring.batch.core.repository.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.dao.JobExecutionDao;
import org.springframework.batch.core.repository.dao.JobInstanceDao;
import org.springframework.batch.core.repository.dao.StepExecutionDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.client.helper.DatabaseClientProvider;
import com.marklogic.junit.spring.AbstractSpringTest;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { com.marklogic.spring.batch.core.repository.dao.MarkLogicDaoConfig.class, com.marklogic.client.spring.BasicConfig.class })
public class MarkLogicJobExecutionDaoTests extends AbstractSpringTest {

	@Autowired
	protected JobExecutionDao dao;
	
	@Autowired
	protected JobInstanceDao jobInstanceDao;

	@Autowired
	protected StepExecutionDao stepExecutionDao;
	
	@Autowired
	DatabaseClientProvider databaseClientProvider;

	protected JobInstance jobInstance;

	protected JobExecution execution;

	protected JobParameters jobParameters;

	/**
	 * @return tested object ready for use
	 */
	protected JobExecutionDao getJobExecutionDao() {
		return dao;
	}

	protected JobInstanceDao getJobInstanceDao() {
		return jobInstanceDao;
	}

	@Before
	public void onSetUp() throws Exception {
		dao = getJobExecutionDao();
		jobParameters = new JobParameters();
		jobInstance = getJobInstanceDao().createJobInstance("execTestJob", jobParameters);
		execution = new JobExecution(jobInstance, new JobParameters());
	}

	/**
	 * Save and find a job execution.
	 */
	@Transactional
	@Test
	public void testSaveAndFind() {

		execution.setStartTime(new Date(System.currentTimeMillis()));
		execution.setLastUpdated(new Date(System.currentTimeMillis()));
		execution.setExitStatus(ExitStatus.UNKNOWN);
		execution.setEndTime(new Date(System.currentTimeMillis()));
		dao.saveJobExecution(execution);

		List<JobExecution> executions = dao.findJobExecutions(jobInstance);
		assertEquals(1, executions.size());
		assertEquals(execution, executions.get(0));
		assertExecutionsAreEqual(execution, executions.get(0));
	}

	/**
	 * Executions should be returned in the reverse order they were saved.
	 */
	@Transactional
	@Test
	public void testFindExecutionsOrdering() {

		List<JobExecution> execs = new ArrayList<>();

		for (int i = 0; i < 10; i++) {
			JobExecution exec = new JobExecution(jobInstance, jobParameters);
			exec.setCreateTime(new Date(i));
			execs.add(exec);
			dao.saveJobExecution(exec);
		}

		List<JobExecution> retrieved = dao.findJobExecutions(jobInstance);
		Collections.reverse(retrieved);

		for (int i = 0; i < 10; i++) {
			assertExecutionsAreEqual(execs.get(i), retrieved.get(i));
		}

	}

	/**
	 * Save and find a job execution.
	 */
	@Transactional
	@Test
	public void testFindNonExistentExecutions() {
		List<JobExecution> executions = dao.findJobExecutions(jobInstance);
		assertEquals(0, executions.size());
	}

	/**
	 * Saving sets id to the entity.
	 */
	@Transactional
	@Test
	public void testSaveAddsIdAndVersion() {

		assertNull(execution.getId());
		assertNull(execution.getVersion());
		dao.saveJobExecution(execution);
		assertNotNull(execution.getId());
		assertNotNull(execution.getVersion());
	}

	/**
	 * Update and retrieve job execution - check attributes have changed as
	 * expected.
	 */
	@Transactional
	@Test
	public void testUpdateExecution() {
		execution.setStatus(BatchStatus.STARTED);
		dao.saveJobExecution(execution);

		execution.setLastUpdated(new Date(0));
		execution.setStatus(BatchStatus.COMPLETED);
		dao.updateJobExecution(execution);

		JobExecution updated = dao.findJobExecutions(jobInstance).get(0);
		assertEquals(execution, updated);
		assertEquals(BatchStatus.COMPLETED, updated.getStatus());
		assertExecutionsAreEqual(execution, updated);
	}

	/**
	 * Check the execution with most recent start time is returned
	 */
	@Transactional
	@Test
	public void testGetLastExecution() {
		JobExecution exec1 = new JobExecution(jobInstance, jobParameters);
		exec1.setCreateTime(new Date(0));

		JobExecution exec2 = new JobExecution(jobInstance, jobParameters);
		exec2.setCreateTime(new Date(1));

		dao.saveJobExecution(exec1);
		dao.saveJobExecution(exec2);

		JobExecution last = dao.getLastJobExecution(jobInstance);
		assertEquals(exec2, last);
	}

	/**
	 * Check the execution is returned
	 */
	@Transactional
	@Test
	public void testGetMissingLastExecution() {
		JobExecution value = dao.getLastJobExecution(jobInstance);
		assertNull(value);
	}

	/**
	 * Check the execution is returned
	 */
	@Transactional
	@Test
	public void testFindRunningExecutions() {

		JobExecution exec = new JobExecution(jobInstance, jobParameters);
		exec.setCreateTime(new Date(0));
		exec.setEndTime(new Date(1L));
		exec.setLastUpdated(new Date(5L));
		dao.saveJobExecution(exec);

		exec = new JobExecution(jobInstance, jobParameters);
		exec.setLastUpdated(new Date(5L));
		exec.createStepExecution("step");
		dao.saveJobExecution(exec);

		if (stepExecutionDao != null) {
			for (StepExecution stepExecution : exec.getStepExecutions()) {
				stepExecutionDao.saveStepExecution(stepExecution);
			}
		}

		Set<JobExecution> values = dao.findRunningJobExecutions(exec.getJobInstance().getJobName());

		assertEquals(1, values.size());
		JobExecution value = values.iterator().next();
		assertEquals(exec, value);
		assertEquals(5L, value.getLastUpdated().getTime());

	}

	/**
	 * Check the execution is returned
	 */
	@Transactional
	@Test
	public void testNoRunningExecutions() {
		Set<JobExecution> values = dao.findRunningJobExecutions("no-such-job");
		assertEquals(0, values.size());
	}

	/**
	 * Check the execution is returned
	 */
	@Transactional
	@Test
	public void testGetExecution() {
		JobExecution exec = new JobExecution(jobInstance, jobParameters);
		exec.setCreateTime(new Date(0));
		exec.createStepExecution("step");

		dao.saveJobExecution(exec);

		if (stepExecutionDao != null) {
			for (StepExecution stepExecution : exec.getStepExecutions()) {
				stepExecutionDao.saveStepExecution(stepExecution);
			}
		}
		JobExecution value = dao.getJobExecution(exec.getId());

		assertEquals(exec, value);
		// N.B. the job instance is not re-hydrated in the JDBC case...
	}

	/**
	 * Check the execution is returned
	 */
	@Transactional
	@Test
	public void testGetMissingExecution() {
		JobExecution value = dao.getJobExecution(54321L);
		assertNull(value);
	}

	/**
	 * Exception should be raised when the version of update argument doesn't
	 * match the version of persisted entity.
	 * 
	 * Ignore this test, using Optimistic Locking capability of MarkLogic, update-policy=VERSION_OPTIONAL
	 */
	@Transactional
	@Test
	@Ignore
	public void testConcurrentModificationException() {

		JobExecution exec1 = new JobExecution(jobInstance, jobParameters);
		dao.saveJobExecution(exec1);
/*
		JobExecution exec2 = new JobExecution(jobInstance, jobParameters);
		exec2.setId(exec1.getId());

		exec2.incrementVersion();
		assertEquals((Integer) 0, exec1.getVersion());
		assertEquals(exec1.getVersion(), exec2.getVersion());
*/
		dao.updateJobExecution(exec1);
		assertEquals((Integer) 1, exec1.getVersion());
		
		//change the content of the JobExecution
		DatabaseClient client = databaseClientProvider.getDatabaseClient();
		ServerEvaluationCall theCall = client.newServerEval();
		String uri = "/projects.spring.io/spring-batch/" + exec1.getId().toString() + ".xml";
		String query = "xdmp:document-insert('" + uri + "', <hello />)";
		logger.info(query);
		theCall.xquery(query);
		theCall.eval();
		
		try {
			dao.updateJobExecution(exec1);
			fail();
		}
		catch (OptimisticLockingFailureException e) {
			// expected
		}

	}

	/**
	 * Successful synchronization from STARTED to STOPPING status.
	 */
	@Transactional
	@Test
	public void testSynchronizeStatusUpgrade() {

		JobExecution exec1 = new JobExecution(jobInstance, jobParameters);
		exec1.setStatus(BatchStatus.STOPPING);
		dao.saveJobExecution(exec1);

		JobExecution exec2 = new JobExecution(jobInstance, jobParameters);
		Assert.state(exec1.getId() != null);
		exec2.setId(exec1.getId());

		exec2.setStatus(BatchStatus.STARTED);
		exec2.setVersion(7);
		Assert.state(exec1.getVersion() != exec2.getVersion());
		Assert.state(exec1.getStatus() != exec2.getStatus());

		dao.synchronizeStatus(exec2);

		assertEquals(exec1.getVersion(), exec2.getVersion());
		assertEquals(exec1.getStatus(), exec2.getStatus());
		
	}

	/**
	 * UNKNOWN status won't be changed by synchronizeStatus, because it is the
	 * 'largest' BatchStatus (will not downgrade).
	 */
	@Transactional
	@Test
	public void testSynchronizeStatusDowngrade() {

		JobExecution exec1 = new JobExecution(jobInstance, jobParameters);
		exec1.setStatus(BatchStatus.STARTED);
		dao.saveJobExecution(exec1);

		JobExecution exec2 = new JobExecution(jobInstance, jobParameters);
		Assert.state(exec1.getId() != null);
		exec2.setId(exec1.getId());

		exec2.setStatus(BatchStatus.UNKNOWN);
		exec2.setVersion(7);
		Assert.state(exec1.getVersion() != exec2.getVersion());
		Assert.state(exec1.getStatus().isLessThan(exec2.getStatus()));

		dao.synchronizeStatus(exec2);

		assertEquals(exec1.getVersion(), exec2.getVersion());
		assertEquals(BatchStatus.UNKNOWN, exec2.getStatus());
	}

	/*
	 * Check to make sure the executions are equal. Normally, comparing the id's
	 * is sufficient. However, for testing purposes, especially of a DAO, we
	 * need to make sure all the fields are being stored/retrieved correctly.
	 */

	private void assertExecutionsAreEqual(JobExecution lhs, JobExecution rhs) {

		assertEquals(lhs.getId(), rhs.getId());
		assertEquals(lhs.getStartTime(), rhs.getStartTime());
		assertEquals(lhs.getStatus(), rhs.getStatus());
		assertEquals(lhs.getEndTime(), rhs.getEndTime());
		assertEquals(lhs.getCreateTime(), rhs.getCreateTime());
		assertEquals(lhs.getLastUpdated(), rhs.getLastUpdated());
		assertEquals(lhs.getVersion(), rhs.getVersion());
	}

}