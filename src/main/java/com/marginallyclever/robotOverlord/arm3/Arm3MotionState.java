package com.marginallyclever.robotOverlord.arm3;

import javax.vecmath.Vector3f;


public class Arm3MotionState {
	// angle of rotation
	public float angleBase = 0;
	public float angleShoulder = 0;
	public float angleElbow = 0;

	// robot arm coordinates.  Relative to base unless otherwise noted.
	public Vector3f fingerPosition;
	public Vector3f fingerForward;
	public Vector3f fingerRight;

	public float iku=0;
	public float ikv=0;
	public float ikw=0;
	
	public Vector3f wrist = new Vector3f();
	public Vector3f elbow = new Vector3f();
	public Vector3f shoulder = new Vector3f();
	
	public Vector3f base = new Vector3f();  // relative to world
	// base orientation, affects entire arm
	public Vector3f base_forward = new Vector3f();
	public Vector3f base_up = new Vector3f();
	public Vector3f base_right = new Vector3f();
	
	// rotating entire robot
	public float base_pan=0;
	public float base_tilt=0;
	
	protected Arm3Dimensions dimensions;
	
	public Arm3MotionState(Arm3Dimensions arg0) {
		dimensions = arg0;
		fingerPosition = arg0.getHomePosition();
		fingerForward = arg0.getHomeForward();
		fingerRight = arg0.getHomeRight();
	}
	
	public void set(Arm3MotionState other) {
		angleBase = other.angleBase;
		angleShoulder = other.angleShoulder;
		angleElbow = other.angleElbow;
		iku=other.iku;
		ikv=other.ikv;
		ikw=other.ikw;
		fingerForward.set(other.fingerForward);
		fingerRight.set(other.fingerRight);
		fingerPosition.set(other.fingerPosition);
		wrist.set(other.wrist);
		elbow.set(other.elbow);
		shoulder.set(other.shoulder);
		base.set(other.base);
		base_forward.set(other.base_forward);
		base_up.set(other.base_up);
		base_right.set(other.base_right);
		base_pan = other.base_pan;
		base_tilt = other.base_tilt;
		dimensions = other.dimensions;
	}
	
	
	//TODO check for collisions with http://geomalgorithms.com/a07-_distance.html#dist3D_Segment_to_Segment ?
	public boolean movePermitted() {
		// don't hit floor
		if(this.fingerPosition.z<0.25f) {
			return false;
		}
		// don't hit ceiling
		if(this.fingerPosition.z>50.0f) {
			return false;
		}

		// check far limit
		Vector3f temp = new Vector3f(this.fingerPosition);
		temp.sub(this.shoulder);
		if(temp.length() > 50) return false;
		// check near limit
		if(temp.length() < dimensions.getBaseToShoulderMinimumLimit()) return false;

		// seems doable
		if(!IK()) return false;
		// angle are good?
		if(!CheckAngleLimits()) return false;

		// OK
		return true;
	}
	
	
	protected boolean CheckAngleLimits() {/*
		// machine specific limits
		if (this.angle_0 < -180) return false;
		if (this.angle_0 >  180) return false;
		if (this.angle_2 <  -20) return false;
		if (this.angle_2 >  180) return false;
		if (this.angle_1 < -150) return false;
		if (this.angle_1 >   80) return false;
		if (this.angle_1 < -this.angle_2+ 10) return false;
		if (this.angle_1 > -this.angle_2+170) return false;

		if (this.angle_3 < -180) return false;
		if (this.angle_3 >  180) return false;
		if (this.angle_4 < -180) return false;
		if (this.angle_4 >  180) return false;
		if (this.angle_5 < -180) return false;
		if (this.angle_5 >  180) return false;*/
		
		return true;
	}
	
	
	/**
	 * Convert cartesian XYZ to robot motor steps.
	 * @input cartesian coordinates relative to the base
	 * @input results where to put resulting angles after the IK calculation
	 * @return 0 if successful, 1 if the IK solution cannot be found.
	 */
	protected boolean IK() {
		float a0,a1,a2;
		// if we know the position of the wrist relative to the shoulder
		// we can use intersection of circles to find the elbow.
		// once we know the elbow position we can find the angle of each joint.
		// each angle can be converted to motor steps.

	    // the finger (attachment point for the tool) is a short distance in "front" of the wrist joint
	    Vector3f finger = new Vector3f(this.fingerPosition);
		this.wrist.set(this.fingerForward);
		this.wrist.scale(-dimensions.getWristToFinger());
		this.wrist.add(finger);
				
	    // use intersection of circles to find two possible elbow points.
	    // the two circles are the bicep (shoulder-elbow) and the ulna (elbow-wrist)
	    // the distance between circle centers is d  
	    Vector3f arm_plane = new Vector3f(this.wrist.x,this.wrist.y,0);
	    arm_plane.normalize();
	
	    this.shoulder.set(arm_plane);
	    this.shoulder.scale(dimensions.getBaseToShoulderX());
	    this.shoulder.z = dimensions.getBaseToShoulderZ();
	    
	    // use intersection of circles to find elbow
	    Vector3f es = new Vector3f(this.wrist);
	    es.sub(this.shoulder);
	    float d = es.length();
	    float r1=dimensions.getElbowToWrist();  // circle 1 centers on wrist
	    float r0=dimensions.getShoulderToElbow();  // circle 0 centers on shoulder
	    if( d > dimensions.getElbowToWrist() + dimensions.getShoulderToElbow() ) {
	      // The points are impossibly far apart, no solution can be found.
	      return false;  // should this throw an error because it's called from the constructor?
	    }
	    float a = ( r0 * r0 - r1 * r1 + d*d ) / ( 2.0f*d );
	    // find the midpoint
	    Vector3f mid=new Vector3f(es);
	    mid.scale(a/d);
	    mid.add(this.shoulder);

	    // with a and r0 we can find h, the distance from midpoint to the intersections.
	    float h=(float)Math.sqrt(r0*r0-a*a);
	    // the distance h on a line orthogonal to n and plane_normal gives us the two intersections.
		Vector3f n = new Vector3f(-arm_plane.y,arm_plane.x,0);
		n.normalize();
		Vector3f r = new Vector3f();
		r.cross(n, es);  // check this!
		r.normalize();
		r.scale(h);

		this.elbow.set(mid);
		this.elbow.sub(r);
		//Vector3f.add(mid, s, elbow);

		
		// find the angle between elbow-shoulder and the horizontal
		Vector3f bicep_forward = new Vector3f(this.elbow);
		bicep_forward.sub(this.shoulder);		  
		bicep_forward.normalize();
		float ax = bicep_forward.dot(arm_plane);
		float ay = bicep_forward.z;
		a1 = (float) -Math.atan2(ay,ax);

		// find the angle between elbow-wrist and the horizontal
		Vector3f ulna_forward = new Vector3f(this.elbow);
		ulna_forward.sub(this.wrist);
		ulna_forward.normalize();
		float bx = ulna_forward.dot(arm_plane);
		float by = ulna_forward.z;
		a2 = (float) Math.atan2(by,bx);

		// find the angle of the base
		a0 = (float) Math.atan2(this.wrist.y,this.wrist.x);
		
		// all angles are in radians, I want degrees
		this.angleBase=(float)Math.toDegrees(a0);
		this.angleShoulder=(float)Math.toDegrees(a1);
		this.angleElbow=(float)Math.toDegrees(a2);

		return true;
	}
	
	
	protected void FK() {
		Vector3f arm_plane = new Vector3f((float)Math.cos(Math.toRadians(this.angleBase)),
					  					  (float)Math.sin(Math.toRadians(this.angleBase)),
					  					  0);
		this.shoulder.set(arm_plane.x*dimensions.getBaseToShoulderX(),
						   arm_plane.y*dimensions.getBaseToShoulderX(),
						   dimensions.getBaseToShoulderZ());
		
		this.elbow.set(arm_plane.x*(float)Math.cos(-Math.toRadians(this.angleShoulder))*dimensions.getShoulderToElbow(),
						arm_plane.y*(float)Math.cos(-Math.toRadians(this.angleShoulder))*dimensions.getShoulderToElbow(),
									(float)Math.sin(-Math.toRadians(this.angleShoulder))*dimensions.getShoulderToElbow());
		this.elbow.add(this.shoulder);

		this.wrist.set(arm_plane.x*(float)Math.cos(Math.toRadians(this.angleElbow))*-dimensions.getElbowToWrist(),
				 		arm_plane.y*(float)Math.cos(Math.toRadians(this.angleElbow))*-dimensions.getElbowToWrist(),
				 					(float)Math.sin(Math.toRadians(this.angleElbow))*-dimensions.getElbowToWrist());
		this.wrist.add(this.elbow);
		
		// build the axies around which we will rotate the tip
		Vector3f fn = new Vector3f();
		Vector3f up = new Vector3f(0,0,1);
		fn.cross(arm_plane,up);
		Vector3f axis = new Vector3f(this.wrist);
		axis.sub(this.elbow);
		axis.normalize();

		this.fingerPosition.set(arm_plane);
		this.fingerPosition.scale(dimensions.getWristToFinger());
		this.fingerPosition.add(this.wrist);

		this.fingerForward.set(this.fingerPosition);
		this.fingerForward.sub(this.wrist);
		this.fingerForward.normalize();
		
		this.fingerRight.set(up); 
		this.fingerRight.scale(-1);
		//this.finger_right = MathHelper.rotateAroundAxis(this.finger_right, axis,-this.angle_3/RAD2DEG);
	}
}
