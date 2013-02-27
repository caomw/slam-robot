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

public class MapNode implements Comparable
{

	int x;
	int y;
	PathPlanner pp = null;

    MapNode(int nx, int ny, PathPlanner npp)
    {
        x = nx;
        y = ny;
        pp = npp;
    }

	public int compareTo(Object o)
	{
		MapNode n = (MapNode) o;
		return (cost() + heuristic()) - (n.cost() + n.heuristic());
	}

	public int heuristic()
	{
		double xy[] = {x,y};
		double goalxy[] = {pp.goal.xyt[0]/pp.travel_cost_map.scale,pp.goal.xyt[1]/pp.travel_cost_map.scale};
		return (int) LinAlg.distance(xy, goalxy,2);
	}

	public int cost()
	{
		return pp.travel_cost_map.cost[x][y];
	}

	public ArrayList<MapNode> neighbors()
	{
		ArrayList<MapNode> ret = new ArrayList<MapNode>();	
		for(int i = 0; i < 4; ++i){
			int deltax = 0;
			int deltay = 0;
			if(i == 0){
				if(x == 0)
					continue;
				ret.add(new MapNode(x - 1, y, pp));
			}
			if(i == 1){
				if(y == 0)
					continue;
				ret.add(new MapNode(x, y - 1, pp));
			}
			if(i == 2){
				if(x ==pp.travel_cost_map.size)
					continue;
				ret.add(new MapNode(x + 1, y, pp));
			}
			if(i == 3){
				if(y ==pp.travel_cost_map.size)
					continue;
				ret.add(new MapNode(x, y+1, pp));
			}
		}
		return ret;
	}

}