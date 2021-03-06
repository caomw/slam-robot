package botlab;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;

import april.jcam.*;
import april.util.*;
import april.jmat.*;
import april.vis.*;
import april.image.*;
import april.jmat.geom.*;

import botlab.lcmtypes.*;

import lcm.lcm.*;

public class RobotController implements LCMSubscriber
{

	LCM lcm;
	bot_status_t bot_status;
	boolean finished = false;
	boolean done_searching = false;
	map_t map;
	String state;
	xyt_t robotPose;
	slam_vector_t slam_vec;
	map_features_t features;

	double angle;
	double prevAngle = 1000;
	int oriented = 0;

	int pastNumTriangles;
	ArrayList<Double> killedTriangles;
	double triangle_to_kill;
	long startTime;

	RobotController()
	{
		try{
			this.lcm = new LCM("udpm://239.255.76.67:7667?ttl=1");
		}catch(IOException e){
			this.lcm = LCM.getSingleton();
		}
		map = null;
		robotPose = null;
		slam_vec = null;
		features = null;
		
		startTime = TimeUtil.utime();

		state = "explore";
		pastNumTriangles = 0;
		killedTriangles = new ArrayList<Double>();

		lcm.subscribe("6_MAP",this);
		lcm.subscribe("6_POSE",this);
		lcm.subscribe("6_FEATURES",this);
		lcm.subscribe("6_SLAM_POSES",this);
	}
	
	
	
	
	public synchronized void messageReceived(LCM lcm, String channel, LCMDataInputStream dins)
	{
		try
		{
			if(finished){
				//return;
			}

			if(channel.equals("6_MAP")){
				if(robotPose != null && bot_status != null && features != null){
					map = new map_t(dins);
					//if(killedTriangles.size() >= 2)state = "spinning";
					//if(TimeUtil.utime() > startTime + 150000000)state = "done";
					if(Math.abs(robotPose.xyt[0]) < 0.10 && Math.abs(robotPose.xyt[1]) < 0.10 && done_searching){
						state = "spinning";
					}
					if(state == "done"){
						xyt_t done_goal = new xyt_t();
						done_goal.xyt = new double[]{0,0,0};
						lcm.publish("6_GOAL", done_goal);
						done_searching = true;
					}
					else if(state == "explore")
					{
						System.out.println("Exploring");
						for(int i = 0; i < slam_vec.numTriangles; i++)
						{
							double distToTarget = LinAlg.distance(slam_vec.triangles[i],robotPose.xyt, 2);
							if(!(killedTriangles.contains(slam_vec.triangles[i][3])) && (distToTarget < .7) && (distToTarget > 0.2))
							{
								System.out.println("Found close triangle");
								System.out.println("Orienting");
								state = "orient";
								triangle_to_kill = slam_vec.triangles[i][3];
								//calculate angle to where slam says it is
								double angle = Math.atan2((slam_vec.triangles[i][1] - robotPose.xyt[1]),(slam_vec.triangles[i][0] - robotPose.xyt[0]));
								System.out.println("angle:" + angle);
								xyt_t alignment_goal = new xyt_t();
								alignment_goal.xyt = new double[]{robotPose.xyt[0], robotPose.xyt[1], robotPose.xyt[2] + (0.9*angle)};
								//publish new goal
								lcm.publish("6_GOAL", alignment_goal);
								return;
							}
						}
						planFrontier();
					}
					else if(state == "orient"){
						
						if(oriented>=3){
							state = "shoot";
							System.out.println("Oriented");
						}

					}
					else if(state == "shoot"){
						oriented = 0;
						System.out.println("Shooting");
						laser_t laser = new laser_t();
						laser.num = 3;
						lcm.publish("6_LASER",laser);
						killedTriangles.add(new Double(triangle_to_kill));
						state = "explore";
						try{
							Thread.sleep(1000);
						}
						catch(InterruptedException e){
							e.printStackTrace();
						}
						
					}
					else if(state == "spinning"){
						laser_t laser = new laser_t();
						laser.num = 3;
						lcm.publish("6_SPIN",laser);
						
					}
				}
				//planFrontier();
/*
				if(robotPose != null && bot_status != null && features != null){
					if(state == "explore"){
						System.out.println("Explore");
						if(Math.abs(robotPose.xyt[0]) < 0.10 && Math.abs(robotPose.xyt[1]) < 0.10 && done_searching){
							state = "spinning";
						}
						if(slam_vec.numTriangles > killedTriangles.size()){
							double min_dist = Double.MAX_VALUE;
							for(int i = 0; i < slam_vec.numTriangles; ++i)
							{
								if(!killedTriangles.contains(new Double(slam_vec.triangles[i][3])))
								{
									double dist = LinAlg.distance(slam_vec.triangles[i],robotPose.xyt,2);
									if(dist < min_dist){
										if(dist < 1)
										{
											min_dist = dist;
											state = "moving to point";
											triangle_to_kill = slam_vec.triangles[i][3];
											break;
										}
									}
									
								}
							}
							if(min_dist == Double.MAX_VALUE)
								planFrontier();
						} else {
							planFrontier();
						}
					}
					else if(state == "moving to point"){
						System.out.println("moving to point");
						double[] triangle = null;
						for(int i = 0; i < slam_vec.numTriangles; ++i){
							if(triangle_to_kill == slam_vec.triangles[i][3]){
								triangle = slam_vec.triangles[i];
							}
						}
						double dist = LinAlg.distance(robotPose.xyt, triangle, 2);
						if(dist < 1.5) { //1 meters shooting distance
							state = "aligning to triangle";
							//calculate angle to where slam says it is
							double angle = Math.atan2((triangle[1] - robotPose.xyt[1]),(triangle[0] - robotPose.xyt[0]));
							xyt_t alignment_goal = new xyt_t();
							alignment_goal.xyt = new double[]{robotPose.xyt[0], robotPose.xyt[1], angle};
							//publish new goal
							lcm.publish("6_GOAL", alignment_goal);
							System.out.println("going to alinging to triangle");
						}else if(dist < 2){
							
							System.out.println("moving closer to triangle");
							xyt_t new_goal = new xyt_t();
							new_goal.xyt = new double[]{triangle[0], triangle[1], triangle[2]};
							lcm.publish("6_GOAL", new_goal);
						}else {
							planFrontier();
							System.out.println("Plan Frontier");
						}
					}
					else if(state == "aligning to triangle"){
						System.out.println("alinging to triangle. Num features found:" + features.ntriangles);
						if(features.ntriangles == 0)state="moving to point";//XXX
						double min_dist = Double.MAX_VALUE;
						int min_triangle = 0;
						for(int i = 0; i < features.ntriangles; ++i){
						if(LinAlg.distance(new double[]{0.0,0.0},features.triangles[i],2) < min_dist){
								min_dist = LinAlg.distance(new double[]{0.0,0.0},features.triangles[i],2);
								min_triangle = i;
							}
						}
						if(min_dist != Double.MAX_VALUE){
									
							double angle = Math.atan2(features.triangles[min_triangle][1], features.triangles[min_triangle][0]);
							System.out.println("alinging to triangle, angle:" + angle);
							System.out.println("features:" + features.triangles[min_triangle][0] + ", " + features.triangles[min_triangle][1]);
							//if angle dead on
							if(angle < 0.02){
								//shoot laser
								System.out.println("KILLing TRIANGLE " );
								laser_t laser = new laser_t();
								laser.num = 3;
								lcm.publish("6_LASER",laser);
								//Laser laser = new Laser();
								//laser.shoot();
								//add triangle_to_kill to killed list
								killedTriangles.add(new Double(triangle_to_kill));
								state = "explore";
								System.out.println("KILLED TRIANGLE " );
							}
							else{
								xyt_t new_angle = new xyt_t();
								new_angle.xyt = new double[]{robotPose.xyt[0],robotPose.xyt[1],robotPose.xyt[2]+angle};
								System.out.println("Delta Angle: " + angle );
								//publish waypoint of angle
								lcm.publish("6_GOAL", new_angle);
							}
						}
					}
				}
				System.out.println(state);
*/
			} else if(channel.equals("6_SLAM_POSES")){
				slam_vec = new slam_vector_t(dins);
				robotPose = slam_vec.xyt[slam_vec.numPoses - 1];
			} else if(channel.equals("6_POSE")){
				bot_status = new bot_status_t(dins);
				if(state == "spinning"){
					//TODO: spinning logic
					//TODO: if done spinning
						//TODO: finished
				}
			} else if(channel.equals("6_FEATURES")){
				features = new map_features_t(dins);
				if((state == "orient")&&(oriented < 3)){
					if(features.ntriangles > 0){
						double minDist = Double.MAX_VALUE;
						double tempDist = 0;
						int j = 0;
						for(int i = 0; i < features.ntriangles; i++){
							tempDist = LinAlg.distance(features.triangles[i],new double[]{0,0,0},2);
							if(tempDist < minDist){
								j = i;
								minDist = tempDist;
							}
						}
						angle = Math.atan2(features.triangles[j][1], features.triangles[j][0]);
						System.out.println("angle:" + angle);
						xyt_t new_angle = new xyt_t();
						new_angle.xyt = new double[]{robotPose.xyt[0],robotPose.xyt[1],robotPose.xyt[2]+angle-Math.toRadians(1)};
						//publish waypoint of angle
						lcm.publish("6_GOAL", new_angle);
						if((angle < .02) || ((angle < prevAngle + 0.0001) && (angle > prevAngle - 0.0001))) {
							oriented ++;
						}
						prevAngle = angle;
					}else state = "explore";
				}
				
			}
			//System.out.println("state at end of recieve message ="+state);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void planFrontier(){
		//frontier planner
		if(state == "explore"){
			boolean knowledge_bounds[][] = new boolean[(int) map.size][(int) map.size]; //is [y][x]
			for(int i = 1; i < map.size-1; ++i){
				for(int j = 1; j < map.size-1; ++j){
					knowledge_bounds[i][j] = false; //default
					if(map.knowledge[i][j] != 1){
						continue;
					}

					int neighbor_count = 0;
					if(map.knowledge[i-1][j-1] == 0) neighbor_count++;
					if(map.knowledge[i-1][j  ] == 0) neighbor_count++;
					if(map.knowledge[i-1][j+1] == 0) neighbor_count++;
					if(map.knowledge[i  ][j-1] == 0) neighbor_count++;

					if(map.knowledge[i  ][j+1] == 0) neighbor_count++;
					if(map.knowledge[i+1][j-1] == 0) neighbor_count++;
					if(map.knowledge[i+1][j  ] == 0) neighbor_count++;
					if(map.knowledge[i+1][j+1] == 0) neighbor_count++;

					if(neighbor_count > 0){ //use to set knowledge threshold
						knowledge_bounds[i][j] = true;
					}
				}
			}

			UnionFind edge_sets = new UnionFind(map.size*map.size);
			edge_sets.FindSets(knowledge_bounds, map.size, map.size);
			HashMap<Integer, Integer> counter = new HashMap<Integer, Integer>();
			HashMap<Integer, ArrayList<Double>> mean_x = new HashMap<Integer, ArrayList<Double>>();
			HashMap<Integer, ArrayList<Double>> mean_y = new HashMap<Integer, ArrayList<Double>>();
			for(int i = 1; i < map.size-1; ++i){
				for(int j = 1; j < map.size-1; ++j){
					if(knowledge_bounds[i][j]) {
						int representative = edge_sets.getRepresentative(i*map.size+j);
						if(counter.containsKey(representative)){
							Integer old_count = counter.get(representative);
							counter.put(representative, new Integer(old_count + 1));
							mean_x.get(representative).add(new Double(i));
							mean_y.get(representative).add(new Double(j));
						} else {
							counter.put(representative, new Integer(1));
							ArrayList<Double> xs = new ArrayList<Double>();
							xs.add(new Double(i));
							mean_x.put(representative, xs);
							ArrayList<Double> ys = new ArrayList<Double>();
							ys.add(new Double(j));
							mean_y.put(representative, ys);
						}
					}
				}
			}
			int max_size = 0;
			double goal_x = 0;
			double goal_y = 0;
			for(Integer i : counter.keySet()){
				int size_not_in_wall = 0;
				for(int j = 0; j < counter.get(i); ++j){
					if((int)(map.cost[mean_x.get(i).get(j).intValue()][mean_y.get(i).get(j).intValue()] & 255) < 255*0.5)
						size_not_in_wall++;
				}
				double lowest_distance = 0;
				for(int j = 0; j < counter.get(i); ++j){
					if((int)(map.cost[mean_x.get(i).get(j).intValue()][mean_y.get(i).get(j).intValue()] & 255) < 255*0.5){
						double[] robotXY = new double[]{robotPose.xyt[0]/map.scale + map.size/2,robotPose.xyt[1]/map.scale + map.size/2};
						double dist = LinAlg.squaredDistance(robotXY, new double[]{mean_x.get(i).get(j), mean_y.get(i).get(j)});
						if(dist > lowest_distance){
							lowest_distance = dist;
							if(size_not_in_wall - 0.5*map.scale*lowest_distance > max_size){
								max_size = (int) (size_not_in_wall - 0.5*map.scale*lowest_distance);
								goal_x = mean_x.get(i).get(j)*map.scale - (map.size/2)*map.scale;
								goal_y = mean_y.get(i).get(j)*map.scale - (map.size/2)*map.scale;
							}
						}
					}
				}
				
			}

			if(max_size < 0.25/map.scale){ //map explored return to start
				goal_x = 0;
				goal_y = 0;
				done_searching = true;
			}

			xyt_t goal = new xyt_t();
			goal.utime = TimeUtil.utime();
			double xyt[] = {goal_x, goal_y, 5.0*Math.PI};
			goal.xyt = xyt;
			lcm.publish("6_GOAL", goal);
		}	
	}
	

	public static void main(String[] args) throws Exception
	{
		RobotController rc = new RobotController();

		while(true)
		{
			Thread.sleep(1000);
		}
	}
}
