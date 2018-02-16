package cloud.aws;

@Grab('com.amazonaws:aws-java-sdk:1.11.277')

import com.amazonaws.services.ecs.*;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.auth.*;
import com.amazonaws.waiters.*;

public class ECS {

	private AmazonECS client;

	public ECS(String key, String secret) {
		this(Regions.US_EAST_1, key, secret);
	}

	public ECS(Regions region, String key, String secret) {
		client = AmazonECSClientBuilder.standard()
			.withRegion(region)
			.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(key, secret)))
			.build();
	}
	
	public String runTask(String cluster, String subnet, String securityGroup, String task) {
		AwsVpcConfiguration vpcConfig = new AwsVpcConfiguration()
			.withAssignPublicIp(AssignPublicIp.ENABLED)
			.withSubnets(subnet)
			.withSecurityGroups(securityGroup);
		RunTaskRequest runRequest = new RunTaskRequest()
			.withCluster(cluster)
			.withCount(1)
			.withLaunchType(LaunchType.FARGATE)
			.withNetworkConfiguration(new NetworkConfiguration().withAwsvpcConfiguration(vpcConfig))
			.withStartedBy('jenkins')
			.withTaskDefinition(task);

		RunTaskResult response = client.runTask(runRequest);
		
		return response.getTasks().get(0).getTaskArn();
	}

	public String lookupTaskIp(String cluster, String taskArn) {
		DescribeTasksRequest describeTaskRequest = new DescribeTasksRequest()
				.withCluster(cluster)
				.withTasks(taskArn);
		client.waiters().tasksRunning().run(new WaiterParameters(describeTaskRequest));

		DescribeTasksResult tasks = client.describeTasks(describeTaskRequest);
		return tasks.getTasks().get(0)
			.getAttachments().find{it.getType().equals('ElasticNetworkInterface')}
			.getDetails().find{it.getName().equals('privateIPv4Address')}
			.getValue();                    
	}

	public def stopTask(String cluster, String taskArn, String reason) {
		client.stopTask(new StopTaskRequest()
			.withCluster(testTaskCluster)
			.withTask(testTaskArn)
			.withReason(reason));
	}

	public def shutdown() {
		client.shutdown();
	}
	
}
