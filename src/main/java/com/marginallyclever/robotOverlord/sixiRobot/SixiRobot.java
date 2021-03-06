package com.marginallyclever.robotOverlord.sixiRobot;

import javax.swing.JPanel;
import javax.vecmath.Vector3f;
import com.jogamp.opengl.GL2;
import com.marginallyclever.communications.NetworkConnection;
import com.marginallyclever.convenience.MathHelper;
import com.marginallyclever.robotOverlord.*;
import com.marginallyclever.robotOverlord.material.Material;
import com.marginallyclever.robotOverlord.sixiRobot.tool.*;
import com.marginallyclever.robotOverlord.model.Model;
import com.marginallyclever.robotOverlord.model.ModelFactory;
import com.marginallyclever.robotOverlord.robot.Robot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Robot Overlord simulation of Sixi 6DOF robot arm.
 * 
 * @author Dan Royer <dan @ marinallyclever.com>
 */
public class SixiRobot
extends Robot {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3644731265897692399L;

	private final static String hello = "HELLO WORLD! I AM ARM6 #";
	private final static String ROBOT_NAME = "Sixi 6DOF arm";
	
	// machine dimensions from design software
	public final static double FLOOR_ADJUST = 0.75538;
	public final static double FLOOR_TO_SHOULDER = 25.8;
	public final static double SHOULDER_TO_ELBOW_Y = 5;
	public final static double SHOULDER_TO_ELBOW_Z = 25;
	public final static double ELBOW_TO_ULNA_Y = -5;
	public final static double ELBOW_TO_ULNA_Z = 10;
	public final static double ULNA_TO_WRIST_Y = 0;
	public final static double ULNA_TO_WRIST_Z = 10;
	public final static double ELBOW_TO_WRIST_Y = ELBOW_TO_ULNA_Y + ULNA_TO_WRIST_Y;
	public final static double ELBOW_TO_WRIST_Z = ELBOW_TO_ULNA_Z + ULNA_TO_WRIST_Z;
	public final static double WRIST_TO_TOOL_Z = 5;
	
	public final static double SHOULDER_TO_ELBOW = Math.sqrt(SHOULDER_TO_ELBOW_Z*SHOULDER_TO_ELBOW_Z + SHOULDER_TO_ELBOW_Y*SHOULDER_TO_ELBOW_Y);
	public final static double ELBOW_TO_WRIST = Math.sqrt(ELBOW_TO_WRIST_Z*ELBOW_TO_WRIST_Z + ELBOW_TO_WRIST_Y*ELBOW_TO_WRIST_Y); 

	private final static Vector3f globalForward = new Vector3f(0,0,1);
	private final static Vector3f globalRight = new Vector3f(1,0,0);
	private final static Vector3f globalUp = new Vector3f(0,1,0);
	
	public static double ADJUST_WRIST_ELBOW_ANGLE = 14.036243;
	public static double ADJUST_SHOULDER_ELBOW_ANGLE = 11.309932;
	//public static double ADJUST_ULNA_ELBOW_ANGLE = 26.56505117707799;
	
	public final static float EPSILON = 0.00001f;
	
	// model files
	private Model floorModel    = null;
	private Model anchorModel   = null;
	private Model shoulderModel = null;
	private Model bicepModel    = null;
	private Model elbowModel    = null;
	private Model forearmModel  = null;
	private Model wristModel    = null;
	private Model handModel     = null;

	private Material floorMat		= null;
	private Material anchorMat		= null;
	private Material shoulderMat	= null;
	private Material bicepMat		= null;
	private Material elbowMat		= null;
	private Material forearmMat		= null;
	private Material wristMat		= null;
	private Material handMat		= null;

	// machine ID
	private long robotUID;
	
	// currently attached tool
	private SixiTool tool = null;
	
	// collision volumes
	private Cylinder [] volumes = new Cylinder[6];

	// motion states
	private SixiRobotKeyframe motionNow = new SixiRobotKeyframe();
	private SixiRobotKeyframe motionFuture = new SixiRobotKeyframe();
	
	// keyboard history
	private float aDir = 0.0f;
	private float bDir = 0.0f;
	private float cDir = 0.0f;
	private float dDir = 0.0f;
	private float eDir = 0.0f;
	private float fDir = 0.0f;

	private float xDir = 0.0f;
	private float yDir = 0.0f;
	private float zDir = 0.0f;
	private float uDir = 0.0f;
	private float vDir = 0.0f;
	private float wDir = 0.0f;

	// machine logic states
	private boolean armMoved 		= false;
	private boolean isPortConfirmed	= false;
	private double stepSize			= 2;
	private double feedRate			= 1000;

	// visual debugging
	private boolean showDebug=false;

	// gui
	protected transient SixiRobotControlPanel armPanel=null;
	
	public SixiRobot() {
		super();
		
		setDisplayName(ROBOT_NAME);
		
		// set up bounding volumes
		for(int i=0;i<volumes.length;++i) {
			volumes[i] = new Cylinder();
		}
		volumes[0].setRadius(3.2f);
		volumes[1].setRadius(3.0f*0.575f);
		volumes[2].setRadius(2.2f);
		volumes[3].setRadius(1.15f);
		volumes[4].setRadius(1.2f);
		volumes[5].setRadius(1.0f*0.575f);
		
		rotateBase(0,0);
		motionNow.set(motionFuture);
		checkAngleLimits(motionNow);
		checkAngleLimits(motionFuture);
		
		setToHomePosition();
		setupMaterials();
		
		tool = new SixiToolGripper();
		tool.attachTo(this);
	}
	
	protected void setupMaterials() {
		floorMat	= new Material();
		anchorMat	= new Material();
		shoulderMat	= new Material();
		bicepMat	= new Material();
		elbowMat	= new Material();
		forearmMat	= new Material();
		wristMat	= new Material();
		handMat		= new Material();

		floorMat   .setDiffuseColor(1.0f,0.0f,0.0f,1);
		anchorMat  .setDiffuseColor(1.0f,0.0f,0.0f,1);
		shoulderMat.setDiffuseColor(1.0f,0.0f,0.0f,1);
		bicepMat   .setDiffuseColor(1.0f,0.0f,0.0f,1);
		elbowMat   .setDiffuseColor(1.0f,0.0f,0.0f,1);
		forearmMat .setDiffuseColor(1.0f,0.0f,0.0f,1);
		wristMat   .setDiffuseColor(1.0f,0.0f,0.0f,1);
		handMat    .setDiffuseColor(1.0f,0.0f,0.0f,1);

		floorMat   .setShininess(2);
		anchorMat  .setShininess(2);
		shoulderMat.setShininess(2);
		bicepMat   .setShininess(2);
		elbowMat   .setShininess(2);
		forearmMat .setShininess(2);
		wristMat   .setShininess(2);
		handMat    .setShininess(2);
	}
	
	@Override
	protected void loadModels(GL2 gl2) {
		try {
			floorModel    = ModelFactory.createModelFromFilename("/Sixi/floor.stl",0.1f);
			anchorModel   = ModelFactory.createModelFromFilename("/Sixi/anchor.stl",0.1f);
			shoulderModel = ModelFactory.createModelFromFilename("/Sixi/shoulder.stl",0.1f);
			bicepModel    = ModelFactory.createModelFromFilename("/Sixi/bicep.stl",0.1f);
			elbowModel    = ModelFactory.createModelFromFilename("/Sixi/elbow.stl",0.1f);
			forearmModel  = ModelFactory.createModelFromFilename("/Sixi/forearm.stl",0.1f);
			wristModel    = ModelFactory.createModelFromFilename("/Sixi/wrist.stl",0.1f);
			handModel     = ModelFactory.createModelFromFilename("/Sixi/hand.stl",0.1f);
			
			bicepModel  .adjustOrigin(new Vector3f(0, 0, -25));
			elbowModel  .adjustOrigin(new Vector3f(0, 5, -50));
			forearmModel.adjustOrigin(new Vector3f(0, 0, -50));
			wristModel  .adjustOrigin(new Vector3f(0, 0, -70));
			handModel   .adjustOrigin(new Vector3f(0, 0, -70));
			
			System.out.println("Sixi loaded OK");
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

    private void readObject(ObjectInputStream inputStream)
            throws IOException, ClassNotFoundException
    {
        inputStream.defaultReadObject();
    }

	@Override
	public ArrayList<JPanel> getContextPanel(RobotOverlord gui) {
		ArrayList<JPanel> list = super.getContextPanel(gui);
		
		//if(armPanel == null) 
			armPanel = new SixiRobotControlPanel(gui,this);
		list.add(armPanel);
		
		//updateGUI();

		ArrayList<JPanel> toolList = tool.getContextPanel(gui);
		Iterator<JPanel> iter = toolList.iterator();
		while(iter.hasNext()) {
			list.add(iter.next());
		}
		
		return list;
	}
	
	public boolean isPortConfirmed() {
		return isPortConfirmed;
	}
	
	private void enableFK() {		
		xDir=0;
		yDir=0;
		zDir=0;
		uDir=0;
		vDir=0;
		wDir=0;
	}
	
	private void disableFK() {	
		aDir=0;
		bDir=0;
		cDir=0;
		dDir=0;
		eDir=0;
		fDir=0;
	}

	/**
	 * 
	 * @param arg0 any value >= 0
	 */
	public void setStepSize(double arg0) {
		if(arg0<0) return;
		stepSize=arg0;
	}
	
	public double getStepSize() {
		return stepSize;
	}

	/**
	 * 
	 * @param arg0 any value >= 0
	 */
	public void setFeedRate(double arg0) {
		if(arg0<0) return;
		feedRate=arg0;
	}
	
	public double getFeedRate() {
		return feedRate;
	}
	
	public void moveA(float dir) {
		aDir=dir;
		enableFK();
	}

	public void moveB(float dir) {
		bDir=dir;
		enableFK();
	}

	public void moveC(float dir) {
		cDir=dir;
		enableFK();
	}

	public void moveD(float dir) {
		dDir=dir;
		enableFK();
	}

	public void moveE(float dir) {
		eDir=dir;
		enableFK();
	}

	public void moveF(float dir) {
		fDir=dir;
		enableFK();
	}

	public void moveX(float dir) {
		xDir=dir;
		disableFK();
	}

	public void moveY(float dir) {
		yDir=dir;
		disableFK();
	}

	public void moveZ(float dir) {
		zDir=dir;
		disableFK();
	}

	public void moveU(float dir) {
		uDir=dir;
		disableFK();
	}

	public void moveV(float dir) {
		vDir=dir;
		disableFK();
	}

	public void moveW(float dir) {
		wDir=dir;
		disableFK();
	}

	public void toggleDebug() {
		showDebug=!showDebug;
	}
	
	public void findHome() {
		// send home command
		if(connection!=null) this.sendLineToRobot("G28");
		setToHomePosition();
		updateGUI();
	}

	protected void setToHomePosition() {
		motionNow.angle0=-45f;
		motionNow.angle1=0f;
		motionNow.angle2=188f;
		motionNow.angle3=0f;
		motionNow.angle4=-90f;
		motionNow.angle5=0f;
		forwardKinematics(motionNow,false,null);
		//inverseKinematics(motionNow);
		motionFuture.set(motionNow);
		//forwardKinematics(motionFuture,false,null);
		//inverseKinematics(motionFuture);
	}
	
	/**
	 * update the desired finger location
	 * @param delta the time since the last update.  Typically ~1/30s
	 */
	protected void updateIK(float delta) {
		boolean changed=false;
		motionFuture.fingerPosition.set(motionNow.fingerPosition);
		final float vel=(float)stepSize;
		float dp = vel;// * delta;

		float dX=motionFuture.fingerPosition.x;
		float dY=motionFuture.fingerPosition.y;
		float dZ=motionFuture.fingerPosition.z;
		
		if (xDir!=0) {
			dX += xDir * dp;
			changed=true;
			xDir=0;
		}		
		if (yDir!=0) {
			dY += yDir * dp;
			changed=true;
			yDir=0;
		}
		if (zDir!=0) {
			dZ += zDir * dp;
			changed=true;
			zDir=0;
		}
		// rotations
		float ru=motionFuture.ikU;
		float rv=motionFuture.ikV;
		float rw=motionFuture.ikW;
		boolean hasTurned=false;

		if (uDir!=0) {
			ru += uDir * dp;
			changed=true;
			hasTurned=true;
			uDir=0;
		}
		if (vDir!=0) {
			rv += vDir * dp;
			changed=true;
			hasTurned=true;
			vDir=0;
		}
		if (wDir!=0) {
			rw += wDir * dp;
			changed=true;
			hasTurned=true;
			wDir=0;
		}


		if(hasTurned) {
			// On a 3-axis robot when homed the forward axis of the finger tip is pointing downward.
			// More complex arms start from the same assumption.
			motionFuture.ikU=ru;
			motionFuture.ikV=rv;
			motionFuture.ikW=rw;

			// Rotating around itself has no effect, so just skip it
			//Vector3f result = MathHelper.rotateAroundAxis(globalForward,globalForward,(float)Math.toRadians(motionFuture.ikU));
			Vector3f result = new Vector3f(globalForward);

			result = MathHelper.rotateAroundAxis(result     ,globalRight  ,(float)Math.toRadians(motionFuture.ikV));
			result = MathHelper.rotateAroundAxis(result     ,globalUp     ,(float)Math.toRadians(motionFuture.ikW));
			motionFuture.fingerForward.set(result);

			result = MathHelper.rotateAroundAxis(globalRight,globalForward,(float)Math.toRadians(motionFuture.ikU));
			result = MathHelper.rotateAroundAxis(result     ,globalRight  ,(float)Math.toRadians(motionFuture.ikV));
			result = MathHelper.rotateAroundAxis(result     ,globalUp     ,(float)Math.toRadians(motionFuture.ikW));
			motionFuture.fingerRight.set(result);
		}
		
		//if(changed==true && motionFuture.movePermitted()) {
		if(changed) {
			motionFuture.fingerPosition.x = dX;
			motionFuture.fingerPosition.y = dY;
			motionFuture.fingerPosition.z = dZ;
			if(!inverseKinematics(motionFuture,false,null)) {
				return;
			}
			if(checkAngleLimits(motionFuture)) {
			//if(motionNow.fingerPosition.epsilonEquals(motionFuture.fingerPosition,0.1f) == false) {
				armMoved=true;

				sendChangeToRealMachine();
				//if(!this.isPortConfirmed()) {
					// live data from the sensors will update motionNow, so only do this if we're unconnected.
					motionNow.set(motionFuture);
				//}
				updateGUI();
			} else {
				motionFuture.set(motionNow);
			}
		}
	}
	
	protected void updateFK(float delta) {
		boolean changed=false;
		float velcd=(float)stepSize; // * delta
		float velabe=(float)stepSize; // * delta

		motionFuture.set(motionNow);
		
		float d0 = motionFuture.angle0;
		float d1 = motionFuture.angle1;
		float d2 = motionFuture.angle2;
		float d3 = motionFuture.angle3;
		float d4 = motionFuture.angle4;
		float d5 = motionFuture.angle5;

		if (fDir!=0) {
			d0 += velabe * fDir;
			changed=true;
			fDir=0;
		}
		
		if (eDir!=0) {
			d1 += velabe * eDir;
			changed=true;
			eDir=0;
		}
		
		if (dDir!=0) {
			d2 += velcd * dDir;
			changed=true;
			dDir=0;
		}

		if (cDir!=0) {
			d3 += velcd * cDir;
			changed=true;
			cDir=0;
		}
		
		if(bDir!=0) {
			d4 += velabe * bDir;
			changed=true;
			bDir=0;
		}
		
		if(aDir!=0) {
			d5 += velabe * aDir;
			changed=true;
			aDir=0;
		}
		

		if(changed) {
			motionFuture.angle5=d5;
			motionFuture.angle4=d4;
			motionFuture.angle3=d3;
			motionFuture.angle2=d2;
			motionFuture.angle1=d1;
			motionFuture.angle0=d0;
			if(checkAngleLimits(motionFuture)) {
				forwardKinematics(motionFuture,false,null);
				armMoved=true;
				
				sendChangeToRealMachine();
				//if(!this.isPortConfirmed()) {
					// live data from the sensors will update motionNow, so only do this if we're unconnected.
					motionNow.set(motionFuture);
				//}
				updateGUI();
			} else {
				motionFuture.set(motionNow);
			}
		}
	}

	protected float roundOff(float v) {
		float SCALE = 1000.0f;
		
		return Math.round(v*SCALE)/SCALE;
	}
	
	public void updateGUI() {
		Vector3f v = new Vector3f();
		v.set(motionNow.fingerPosition);
		v.add(getPosition());
		armPanel.xPos.setText(Float.toString(roundOff(v.x)));
		armPanel.yPos.setText(Float.toString(roundOff(v.y)));
		armPanel.zPos.setText(Float.toString(roundOff(v.z)));
		armPanel.uPos.setText(Float.toString(roundOff(motionNow.ikU)));
		armPanel.vPos.setText(Float.toString(roundOff(motionNow.ikV)));
		armPanel.wPos.setText(Float.toString(roundOff(motionNow.ikW)));

		armPanel.angle5.setText(Float.toString(roundOff(motionNow.angle5)));
		armPanel.angle4.setText(Float.toString(roundOff(motionNow.angle4)));
		armPanel.angle3.setText(Float.toString(roundOff(motionNow.angle3)));
		armPanel.angle2.setText(Float.toString(roundOff(motionNow.angle2)));
		armPanel.angle1.setText(Float.toString(roundOff(motionNow.angle1)));
		armPanel.angle0.setText(Float.toString(roundOff(motionNow.angle0)));

		if( tool != null ) tool.updateGUI();
	}

	protected void sendChangeToRealMachine() {
		if(!isPortConfirmed) return;
		
		String str="";
		if(motionFuture.angle0!=motionNow.angle0) str+=" X"+roundOff(motionFuture.angle0);
		if(motionFuture.angle1!=motionNow.angle1) str+=" Y"+roundOff(motionFuture.angle1);
		if(motionFuture.angle2!=motionNow.angle2) str+=" Z"+roundOff(motionFuture.angle2);
		if(motionFuture.angle3!=motionNow.angle3) str+=" U"+roundOff(motionFuture.angle3);
		if(motionFuture.angle4!=motionNow.angle4) str+=" V"+roundOff(motionFuture.angle4);
		if(motionFuture.angle5!=motionNow.angle5) str+=" W"+roundOff(motionFuture.angle5);
		if(str.length()>0) {
			str+=" F"+roundOff((float)feedRate);
			System.out.println(str);
			this.sendLineToRobot("G0"+str);
		}
	}
	
	@Override
	public void prepareMove(float delta) {
		updateIK(delta);
		updateFK(delta);
		if(tool != null) tool.update(delta);
	}

	@Override
	public void finalizeMove() {
		// copy motion_future to motion_now
		motionNow.set(motionFuture);
		
		if(armMoved) {
			if( this.isReadyToReceive ) {
				armMoved=false;
			}
		}
	}
	
	@Override
	public void render(GL2 gl2) {
		super.render(gl2);
		
		gl2.glPushMatrix();
			// TODO rotate model
			Vector3f p = getPosition();
			gl2.glTranslatef(p.x, p.y, p.z);

			gl2.glTranslated(motionNow.base.x,motionNow.base.y,motionNow.base.z+FLOOR_ADJUST);	
			
			gl2.glPushMatrix();
			renderModels(gl2);
			gl2.glPopMatrix();
			
			if(showDebug) {
				gl2.glDisable(GL2.GL_DEPTH_TEST);
				boolean lightOn= gl2.glIsEnabled(GL2.GL_LIGHTING);
				boolean matCoOn= gl2.glIsEnabled(GL2.GL_COLOR_MATERIAL);
				gl2.glDisable(GL2.GL_LIGHTING);
				gl2.glDisable(GL2.GL_COLOR_MATERIAL);
				
				gl2.glPushMatrix();	
				forwardKinematics(motionNow,true,gl2);
				gl2.glPopMatrix();

				gl2.glPushMatrix();
				inverseKinematics(motionNow,true,gl2);
				gl2.glPopMatrix();
				
				if(lightOn) gl2.glEnable(GL2.GL_LIGHTING);
				if(matCoOn) gl2.glEnable(GL2.GL_COLOR_MATERIAL);
				gl2.glEnable(GL2.GL_DEPTH_TEST);
			}
		gl2.glPopMatrix();
	}
	
	/**
	 * Draw the physical model according to the angle values in the motionNow state.
	 * @param gl2 the openGL render context
	 */
	protected void renderModels(GL2 gl2) {
		// floor
		floorMat.render(gl2);
		floorModel.render(gl2);
		
		// anchor
		anchorMat.render(gl2);
		anchorModel.render(gl2);

		// shoulder
		gl2.glRotated(90+motionNow.angle0,0,0,1);
		//shoulderMat.setSpecularColor(0, 0, 0, 1);
		//shoulderMat.setDiffuseColor(1, 0, 0, 1);
		//shoulderMat.setShininess(50);
		shoulderMat.render(gl2);
		shoulderModel.render(gl2);
		
		// bicep
		gl2.glTranslated( 0, 0, FLOOR_TO_SHOULDER-FLOOR_ADJUST);
		gl2.glRotated(motionNow.angle1-90, 1, 0, 0);
		bicepMat.render(gl2);
		bicepModel.render(gl2);

		// elbow
		//drawMatrix(gl2,new Vector3f(0,0,0),new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1),10);
		gl2.glTranslated(0,-SHOULDER_TO_ELBOW_Y,SHOULDER_TO_ELBOW_Z);
		gl2.glRotated(-motionNow.angle2+180, 1, 0, 0);
		elbowMat.render(gl2);
		elbowModel.render(gl2);

		gl2.glTranslated(0,-ELBOW_TO_ULNA_Y,0);
		gl2.glRotated(motionNow.angle3,0,0,1);
		forearmMat.render(gl2);
		forearmModel.render(gl2);
		
		// wrist
		gl2.glTranslated(0, 0, ELBOW_TO_WRIST_Z);
		gl2.glRotated(motionNow.angle4,1,0,0);
		wristMat.render(gl2);
		wristModel.render(gl2);
		
		// hand
		gl2.glRotated(-motionNow.angle5,0,0,1);
		handMat.render(gl2);
		handModel.render(gl2);
		
		// tool
		if(tool!=null) {
			gl2.glTranslated(0,0,WRIST_TO_TOOL_Z);
			gl2.glRotated(90, 0, 1, 0);
			// tool has its own material.
			//tool.render(gl2);
		}
	}
	
	/**
	 * @see drawMatrix(gl2,p,u,v,w,1)
	 * @param gl2
	 * @param p
	 * @param u
	 * @param v
	 * @param w
	 */
	protected void drawMatrix(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w) {
		drawMatrix(gl2,p,u,v,w,1);
	}
	
	/**
	 * Draw the three vectors of a matrix at a point
	 * @param gl2 render context
	 * @param p position at which to draw
	 * @param u in yellow (1,1,0)
	 * @param v in teal (0,1,1)
	 * @param w in magenta (1,0,1)
	 * @param scale nominally 1
	 */
	protected void drawMatrix(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w,float scale) {
		boolean depthWasOn = gl2.glIsEnabled(GL2.GL_DEPTH_TEST);
		gl2.glDisable(GL2.GL_DEPTH_TEST);
			
		gl2.glPushMatrix();
			gl2.glTranslatef(p.x, p.y, p.z);
			gl2.glScalef(scale, scale, scale);
			
			gl2.glBegin(GL2.GL_LINES);
			gl2.glColor3f(1,1,0);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(u.x,u.y,u.z);  // 1,1,0 = yellow
			gl2.glColor3f(0,1,1);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(v.x,v.y,v.z);  // 0,1,1 = teal 
			gl2.glColor3f(1,0,1);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(w.x,w.y,w.z);  // 1,0,1 = magenta
			gl2.glEnd();

		gl2.glPopMatrix();
		
		if(depthWasOn) gl2.glEnable(GL2.GL_DEPTH_TEST);
	}

	/**
	 * @see drawMatrix(gl2,p,u,v,w,1)
	 * @param gl2
	 * @param p
	 * @param u
	 * @param v
	 * @param w
	 */
	protected void drawMatrix2(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w) {
		drawMatrix2(gl2,p,u,v,w,1);
	}
	
	/**
	 * Draw the three vectors of a matrix at a point
	 * @param gl2 render context
	 * @param p position at which to draw
	 * @param u in red
	 * @param v in green
	 * @param w in blue
	 * @param scale nominally 1
	 */
	protected void drawMatrix2(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w,float scale) {
		boolean depthWasOn = gl2.glIsEnabled(GL2.GL_DEPTH_TEST);
		gl2.glDisable(GL2.GL_DEPTH_TEST);
			
		gl2.glPushMatrix();
			gl2.glTranslatef(p.x, p.y, p.z);
			gl2.glScalef(scale, scale, scale);
			
			gl2.glBegin(GL2.GL_LINES);
			gl2.glColor3f(1,0,0);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(u.x,u.y,u.z);  // 1,0,0 = red
			gl2.glColor3f(0,1,0);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(v.x,v.y,v.z);  // 0,1,0 = green 
			gl2.glColor3f(0,0,1);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(w.x,w.y,w.z);  // 0,0,1 = blue
			gl2.glEnd();

		gl2.glPopMatrix();
		
		if(depthWasOn) gl2.glEnable(GL2.GL_DEPTH_TEST);
	}
	
	protected void drawBounds(GL2 gl2) {
		throw new UnsupportedOperationException();
	}
	
	private double parseNumber(String str) {
		float f=0;
		try {
			f = Float.parseFloat(str);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		return f;
	}

	public void setModeAbsolute() {
		if(connection!=null) this.sendLineToRobot("G90");
	}
	
	public void setModeRelative() {
		if(connection!=null) this.sendLineToRobot("G91");
	}
	
	@Override
	// override this method to check that the software is connected to the right type of robot.
	public void dataAvailable(NetworkConnection arg0,String line) {
		if(line.contains(hello)) {
			isPortConfirmed=true;
			//finalizeMove();
			setModeAbsolute();
			this.sendLineToRobot("R1");
			
			String uidString=line.substring(hello.length()).trim();
			System.out.println(">>> UID="+uidString);
			try {
				long uid = Long.parseLong(uidString);
				if(uid==0) {
					robotUID = getNewRobotUID();
				} else {
					robotUID = uid;
				}
				armPanel.setUID(robotUID);
			}
			catch(Exception e) {
				e.printStackTrace();
			}

			setDisplayName(ROBOT_NAME+" #"+robotUID);
		}
		
		if( isPortConfirmed ) {
			if(line.startsWith("A") && !line.startsWith("As")) {
				String items[] = line.split(" ");
				try {
					if(items.length>=5) {
						for(int i=0;i<items.length;++i) {
							if(items[i].startsWith("A")) {
								float v = (float)parseNumber(items[i].substring(1));
								if(motionFuture.angle5 != v) {
									motionFuture.angle5 = v;
									armPanel.angle5.setText(Float.toString(roundOff(v)));
								}
							} else if(items[i].startsWith("B")) {
								float v = (float)parseNumber(items[i].substring(1));
								if(motionFuture.angle4 != v) {
									motionFuture.angle4 = v;
									armPanel.angle4.setText(Float.toString(roundOff(v)));
								}
							} else if(items[i].startsWith("C")) {
								float v = (float)parseNumber(items[i].substring(1));
								if(motionFuture.angle3 != v) {
									motionFuture.angle3 = v;
									armPanel.angle3.setText(Float.toString(roundOff(v)));
								}
							} else if(items[i].startsWith("D")) {
								float v = (float)parseNumber(items[i].substring(1));
								if(motionFuture.angle2 != v) {
									motionFuture.angle2 = v;
									armPanel.angle2.setText(Float.toString(roundOff(v)));
								}
							} else if(items[i].startsWith("E")) {
								float v = (float)parseNumber(items[i].substring(1));
								if(motionFuture.angle1 != v) {
									motionFuture.angle1 = v;
									armPanel.angle1.setText(Float.toString(roundOff(v)));
								}
							}
						}
						
						forwardKinematics(motionFuture,false,null);
						motionNow.set(motionFuture);
						updateGUI();
					}
				} catch(java.lang.NumberFormatException e) {
					System.out.print("*** "+line);
				}
			} else {
				System.out.print("*** "+line);
			}
		}
	}

	public void moveBase(Vector3f dp) {
		motionFuture.anchorPosition.set(dp);
	}
	
	public void rotateBase(double pan,double tilt) {
		motionFuture.basePan=pan;
		motionFuture.baseTilt=tilt;
		
		pan = Math.toRadians(pan);
		tilt = Math.toRadians(tilt);
		
		motionFuture.baseForward.y = (float)Math.sin(pan) * (float)Math.cos(tilt);
		motionFuture.baseForward.x = (float)Math.cos(pan) * (float)Math.cos(tilt);
		motionFuture.baseForward.z =                        (float)Math.sin(tilt);
		motionFuture.baseForward.normalize();
		
		motionFuture.baseUp.set(0,0,1);
	
		motionFuture.baseRight.cross(motionFuture.baseForward, motionFuture.baseUp);
		motionFuture.baseRight.normalize();
		motionFuture.baseUp.cross(motionFuture.baseRight, motionFuture.baseForward);
		motionFuture.baseUp.normalize();
	}
	
	public BoundingVolume [] getBoundingVolumes() {
		// shoulder joint
		Vector3f t1=new Vector3f(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.shoulder);
		Vector3f t2=new Vector3f(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.shoulder);
		volumes[0].SetP1(getWorldCoordinatesFor(t1));
		volumes[0].SetP2(getWorldCoordinatesFor(t2));
		// bicep
		volumes[1].SetP1(getWorldCoordinatesFor(motionFuture.shoulder));
		volumes[1].SetP2(getWorldCoordinatesFor(motionFuture.elbow));
		// elbow
		t1.set(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.elbow);
		t2.set(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.elbow);
		volumes[2].SetP1(getWorldCoordinatesFor(t1));
		volumes[2].SetP2(getWorldCoordinatesFor(t2));
		// ulna
		volumes[3].SetP1(getWorldCoordinatesFor(motionFuture.elbow));
		volumes[3].SetP2(getWorldCoordinatesFor(motionFuture.wrist));
		// wrist
		t1.set(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.wrist);
		t2.set(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.wrist);
		volumes[4].SetP1(getWorldCoordinatesFor(t1));
		volumes[4].SetP2(getWorldCoordinatesFor(t2));
		// finger
		volumes[5].SetP1(getWorldCoordinatesFor(motionFuture.wrist));
		volumes[5].SetP2(getWorldCoordinatesFor(motionFuture.fingerPosition));
		
		return volumes;
	}
	
	Vector3f getWorldCoordinatesFor(Vector3f in) {
		Vector3f out = new Vector3f(motionFuture.anchorPosition);
		
		Vector3f tempx = new Vector3f(motionFuture.baseForward);
		tempx.scale(in.x);
		out.add(tempx);

		Vector3f tempy = new Vector3f(motionFuture.baseRight);
		tempy.scale(-in.y);
		out.add(tempy);

		Vector3f tempz = new Vector3f(motionFuture.baseUp);
		tempz.scale(in.z);
		out.add(tempz);
				
		return out;
	}

	/**
	 * Query the web server for a new robot UID.  
	 * @return the new UID if successful.  0 on failure.
	 * @see <a href='http://www.exampledepot.com/egs/java.net/Post.html'>http://www.exampledepot.com/egs/java.net/Post.html</a>
	 */
	private long getNewRobotUID() {
		long new_uid = 0;

		try {
			// Send data
			URL url = new URL("https://marginallyclever.com/evil_minion_getuid.php");
			URLConnection conn = url.openConnection();
			try (
                    final InputStream connectionInputStream = conn.getInputStream();
                    final Reader inputStreamReader = new InputStreamReader(connectionInputStream, StandardCharsets.UTF_8);
                    final BufferedReader rd = new BufferedReader(inputStreamReader)
					) {
				String line = rd.readLine();
				new_uid = Long.parseLong(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}

		// did read go ok?
		if (new_uid != 0) {
			// make sure a topLevelMachinesPreferenceNode node is created
			// tell the robot it's new UID.
			this.sendLineToRobot("UID " + new_uid);
		}
		return new_uid;
	}
	
	// TODO check for collisions with http://geomalgorithms.com/a07-_distance.html#dist3D_Segment_to_Segment ?
	public boolean movePermitted(SixiRobotKeyframe keyframe) {
		// don't hit floor?
		// don't hit ceiling?

		// check far limit
		// seems doable
		if(!inverseKinematics(keyframe,false,null)) return false;
		// angle are good?
		if(!checkAngleLimits(keyframe)) return false;

		// OK
		return true;
	}

	// machine specific limits
	protected boolean checkAngleLimits(SixiRobotKeyframe keyframe) {
		if (keyframe.angle0 <  -90) { System.out.println("angle0 top "+keyframe.angle0);	return false; }
		if (keyframe.angle0 >  265) { System.out.println("angle0 bottom "+keyframe.angle0);	return false; }
		
		if (keyframe.angle1 <    0) { System.out.println("angle1 top "+keyframe.angle1);	return false; }
		if (keyframe.angle1 >  180) { System.out.println("angle1 bottom "+keyframe.angle1);	return false; }
		
		if (keyframe.angle2 <    5) { System.out.println("angle2 top "+keyframe.angle2);	return false; }
		if (keyframe.angle2 >  188) { System.out.println("angle2 bottom "+keyframe.angle2);	return false; }
		
		if (keyframe.angle3 <    0) { System.out.println("angle3 top "+keyframe.angle3);	return false; }
		if (keyframe.angle3 >  355) { System.out.println("angle3 bottom "+keyframe.angle3);	return false; }
		
		if (keyframe.angle4 <  -90) { System.out.println("angle4 top "+keyframe.angle4);	return false; }
		if (keyframe.angle4 >   90) { System.out.println("angle4 bottom "+keyframe.angle4);	return false; }
		
		if (keyframe.angle5 <    0) { System.out.println("angle5 top "+keyframe.angle5);	return false; }
		if (keyframe.angle5 >  355) { System.out.println("angle5 bottom "+keyframe.angle5);	return false; }

		return true;
	}
	
	/**
	 * Knowing the position and orientation of the finger, find the angles at each joint.
	 * @return false if successful, true if the IK solution cannot be found.
	 * @param renderMode don't apply math, just visualize the intermediate results
	 */
	protected boolean inverseKinematics(SixiRobotKeyframe keyframe,boolean renderMode,GL2 gl2) {
		double ee;
		float n, xx, yy, angle0,angle1,angle2,angle3,angle4,angle5;
		
		// rotation at finger, bend at wrist, rotation between wrist and elbow, then bends down to base.

		// get the finger position
		Vector3f fingerPlaneZ = new Vector3f(keyframe.fingerForward);
		Vector3f fingerPlaneX = new Vector3f(keyframe.fingerRight);
		Vector3f fingerPlaneY = new Vector3f();
		fingerPlaneY.cross(fingerPlaneZ, fingerPlaneX);

		// find the wrist position
		Vector3f wristToFinger = new Vector3f(fingerPlaneZ);
		n = (float)SixiRobot.WRIST_TO_TOOL_Z;
		wristToFinger.scale(n);
		
		Vector3f wristPosition = new Vector3f(keyframe.fingerPosition);
		wristPosition.sub(wristToFinger);

		// figure out the shoulder matrix
		Vector3f shoulderPosition = new Vector3f(0,0,(float)(FLOOR_TO_SHOULDER));
		
		if(Math.abs(wristPosition.x)<EPSILON && Math.abs(wristPosition.y)<EPSILON) {
			// Wrist is directly above shoulder, makes calculations hard.
			// TODO figure this out.  Use previous state to guess elbow?
			return false;
		}
		Vector3f shoulderPlaneX = new Vector3f(wristPosition.x,wristPosition.y,0);
		shoulderPlaneX.normalize();
		Vector3f shoulderPlaneZ = new Vector3f(0,0,1);
		Vector3f shoulderPlaneY = new Vector3f();
		shoulderPlaneY.cross(shoulderPlaneX, shoulderPlaneZ);
		shoulderPlaneY.normalize();

		// Find elbow by using intersection of circles (http://mathworld.wolfram.com/Circle-CircleIntersection.html)
		// x = (dd-rr+RR) / (2d)
		Vector3f shoulderToWrist = new Vector3f(wristPosition);
		shoulderToWrist.sub(shoulderPosition);
		float d = shoulderToWrist.length();
		float R = (float)Math.abs(SixiRobot.SHOULDER_TO_ELBOW);
		float r = (float)Math.abs(SixiRobot.ELBOW_TO_WRIST);
		if( d > R+r ) {
			// impossibly far away
			return false;
		}
		float x = (d*d - r*r + R*R ) / (2*d);
		if( x > R ) {
			// would cause sqrt(-something)
			return false;
		}
		shoulderToWrist.normalize();
		Vector3f elbowPosition = new Vector3f(shoulderToWrist);
		elbowPosition.scale(x);
		elbowPosition.add(shoulderPosition);
		// v1 is now at the intersection point between ik_wrist and ik_boom
		Vector3f v1 = new Vector3f();
		float a = (float)( Math.sqrt( R*R - x*x ) );
		v1.cross(shoulderPlaneY, shoulderToWrist);
		Vector3f v1neg = new Vector3f(v1);
		// find both possible intersections of circles
		v1.scale(a);
		v1neg.scale(-a);
		v1.add(elbowPosition);
		v1neg.add(elbowPosition);
		// the closer of the two circles to the previous elbow position is probably the more desirable of the two.
		{
			Vector3f test1 = new Vector3f(keyframe.elbow);
			test1.sub(v1);
			float test1LenSquared = test1.lengthSquared();

			Vector3f test1neg = new Vector3f(keyframe.elbow);
			test1neg.sub(v1neg);
			float test1negLenSquared = test1neg.lengthSquared();
			
			if(test1LenSquared < test1negLenSquared) {
				elbowPosition.set(v1);				
			} else {
				elbowPosition.set(v1neg);
			}
		}

		// All the joint locations are now known.
		// Now I have to build some matrices to find the correct angles because the sixi has those L shaped bones.
		Vector3f elbowToWrist = new Vector3f(wristPosition);
		elbowToWrist.sub(elbowPosition);
		elbowToWrist.normalize();
		v1.cross(elbowToWrist,shoulderPlaneY);
		Vector3f v2 = new Vector3f();
		v2.cross(v1,shoulderPlaneY);
		v2.normalize();  // normalized version of elbowToWrist 
		
		Vector3f nvx = new Vector3f();
		Vector3f nvy = new Vector3f();

		nvx.set(v1);	nvx.scale((float)Math.cos(Math.toRadians(ADJUST_WRIST_ELBOW_ANGLE)));
		nvy.set(v2);	nvy.scale((float)Math.sin(Math.toRadians(ADJUST_WRIST_ELBOW_ANGLE)));
		Vector3f elbowPlaneX = new Vector3f(nvx);
		Vector3f elbowPlaneZ = new Vector3f();
		elbowPlaneX.add(nvy);
		elbowPlaneX.normalize();
		elbowPlaneZ.cross(shoulderPlaneY,elbowPlaneX);


		Vector3f shoulderToElbow = new Vector3f(elbowPosition);
		shoulderToElbow.sub(shoulderPosition);
		shoulderToElbow.normalize();
		
		v1.set(shoulderToElbow);
		v1.normalize();
		v2.cross(shoulderPlaneY,v1);
		nvx.set(v1);	nvx.scale((float)Math.cos(Math.toRadians(ADJUST_SHOULDER_ELBOW_ANGLE)));
		nvy.set(v2);	nvy.scale((float)Math.sin(Math.toRadians(ADJUST_SHOULDER_ELBOW_ANGLE)));
		Vector3f bicepPlaneZ = new Vector3f(nvx);
		Vector3f bicepPlaneX = new Vector3f();
		bicepPlaneZ.add(nvy);
		bicepPlaneZ.normalize();
		bicepPlaneX.cross(bicepPlaneZ,shoulderPlaneY);
		
		// ulna matrix
		Vector3f ulnaPlaneZ = new Vector3f(elbowPlaneZ);
		Vector3f ulnaPlaneY = new Vector3f();
		Vector3f ulnaPlaneX = new Vector3f();
		ulnaPlaneZ.normalize();
		
		// I have wristToFinger.  I need wristToFinger projected on the plane elbow-space XY to calculate the angle. 
		float tf = elbowPlaneZ.dot(keyframe.fingerForward);
		// v0 and keyframe.fingerForward are normal length.  if they dot to nearly 1, they are colinear.
		// if they are colinear then I have no reference to calculate the angle of the ulna rotation.
		if(tf>=1-EPSILON) {
			return false;
		}

		tf = elbowPlaneZ.dot(wristToFinger);
		Vector3f projectionAmount = new Vector3f(elbowPlaneZ);
		projectionAmount.scale(tf);
		ulnaPlaneX.set(wristToFinger);
		ulnaPlaneX.sub(projectionAmount);
		ulnaPlaneX.normalize();
		ulnaPlaneY.cross(ulnaPlaneX,ulnaPlaneZ);
		ulnaPlaneY.normalize();

		// TODO wrist may be bending backward.  As it passes the middle a singularity can occur.
		// Compare projected vector to previous frame's projected vector. if the direction is reversed, flip it. 

		// wrist matrix
		Vector3f wristPlaneZ = new Vector3f(wristToFinger);
		wristPlaneZ.normalize();
		Vector3f wristPlaneY = new Vector3f(ulnaPlaneY);
		Vector3f wristPlaneX = new Vector3f();
		wristPlaneX.cross(wristPlaneY,wristPlaneZ);
		wristPlaneX.normalize();

		
		// find the angles

		// shoulder
		ee = Math.atan2(shoulderPlaneX.y, shoulderPlaneX.x);
		ee = MathHelper.capRotationRadians(ee);
		angle0 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee));

		// bicep
		xx = (float)shoulderToElbow.z;
		yy = shoulderPlaneX.dot(shoulderToElbow);
		ee = Math.atan2(yy, xx);
		angle1 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee)+90-ADJUST_SHOULDER_ELBOW_ANGLE);
		
		// elbow
		xx = (float)shoulderToElbow.dot(elbowToWrist);
		v1.cross(shoulderPlaneY,shoulderToElbow);
		yy = elbowToWrist.dot(v1);
		ee = Math.atan2(yy, xx);
		angle2 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee)+180-ADJUST_SHOULDER_ELBOW_ANGLE-ADJUST_WRIST_ELBOW_ANGLE);
		
		// ulna rotation
		xx = shoulderPlaneY.dot(ulnaPlaneX);  // shoulderPlaneY is the same as elbowPlaneY
		yy = elbowPlaneX   .dot(ulnaPlaneX);
		ee = Math.atan2(yy, xx);
		double ee1 = Math.atan2(yy, xx);
		double ee2 = Math.atan2(-yy, -xx);
		float angle3a = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee1)+90);
		float angle3b = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee2)+90);
		if(angle3a> 180) angle3a-=360;
		if(angle3a<-180) angle3a+=360;
		if(angle3b> 180) angle3b-=360;
		if(angle3b<-180) angle3b+=360;
		float ada = Math.abs(angle3a - keyframe.angle3);
		float adb = Math.abs(angle3b - keyframe.angle3);
		boolean flipWrist = false;
		if( ada < adb ) {
			angle3 = angle3a;
		} else {
			angle3 = angle3b;
			flipWrist=true;
		}
		//angle3 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee)+90);
		//System.out.println(angle3a + "\t"+angle3b + "\t" + keyframe.angle3);
		
		// wrist
		xx = ulnaPlaneX.dot(wristToFinger);
		yy = ulnaPlaneZ.dot(wristToFinger);
		ee = Math.atan2(yy, xx);
		angle4 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee)-90);
		if(flipWrist) {
			angle4 = -angle4;
		}
		if( angle4 > 270 ) angle4 -= 360;
		if( angle4 <-270 ) angle4 += 360;
		if(Math.abs(angle4 - keyframe.angle4)>90) {
			System.out.println("angle4 jump "+angle4+" vs "+keyframe.angle4);
		}
		
		// hand		
		xx = wristPlaneY.dot(keyframe.fingerRight);
		yy = wristPlaneX.dot(keyframe.fingerRight);
		ee = Math.atan2(yy, xx);
		angle5 = (float)MathHelper.capRotationDegrees(Math.toDegrees(ee)-90);
		
		if(gl2!=null) {
			// get elbow to ulna
			v1.set(elbowPlaneX);	v1.scale((float)SixiRobot.ELBOW_TO_ULNA_Y);
			v2.set(elbowPlaneZ);	v2.scale((float)SixiRobot.ELBOW_TO_ULNA_Z);
			Vector3f elbowToUlna = new Vector3f();
			elbowToUlna.add(v1);
			elbowToUlna.add(v2);
			Vector3f ulnaPosition = new Vector3f(elbowPosition);
			ulnaPosition.add(elbowToUlna);
			
			drawMatrix2(gl2,keyframe.fingerPosition,fingerPlaneX,fingerPlaneY,fingerPlaneZ,3);
			drawMatrix2(gl2,wristPosition,wristPlaneX,wristPlaneY,wristPlaneZ,2);
			drawMatrix2(gl2,ulnaPosition,ulnaPlaneX,ulnaPlaneY,elbowPlaneZ);
			drawMatrix2(gl2,elbowPosition,elbowPlaneX,shoulderPlaneY,elbowPlaneZ);
			drawMatrix2(gl2,shoulderPosition,bicepPlaneX,shoulderPlaneY,bicepPlaneZ);
			
			//gl2.glTranslated(keyframe.fingerPosition.x, keyframe.fingerPosition.y, keyframe.fingerPosition.z);
			gl2.glBegin(GL2.GL_LINE_STRIP);
			gl2.glColor3f(1,1,1);
			gl2.glVertex3d(
					keyframe.fingerPosition.x,
					keyframe.fingerPosition.y,
					keyframe.fingerPosition.z);
			gl2.glVertex3d(
					keyframe.fingerPosition.x-projectionAmount.x,
					keyframe.fingerPosition.y-projectionAmount.y,
					keyframe.fingerPosition.z-projectionAmount.z);
			gl2.glVertex3d(wristPosition.x,wristPosition.y,wristPosition.z);
			gl2.glEnd();
		}
		if(!renderMode) {
			keyframe.base = new Vector3f(0,0,0);
			keyframe.shoulder.set(shoulderPosition);
			keyframe.elbow.set(elbowPosition);
			keyframe.wrist.set(wristPosition);
			keyframe.angle0 = angle0;
			keyframe.angle1 = angle1;
			keyframe.angle2 = angle2;
			keyframe.angle3 = angle3;
			keyframe.angle4 = angle4;
			keyframe.angle5 = angle5;
		}

		return true;
	}
	
	/**
	 * Calculate the finger location from the angles at each joint
	 * @param keyframe
	 * @param renderMode don't apply math, just visualize the intermediate results
	 */
	protected void forwardKinematics(SixiRobotKeyframe keyframe,boolean renderMode,GL2 gl2) {
		double angle0rad = Math.toRadians(keyframe.angle0);
		double angle1rad = Math.toRadians(180-keyframe.angle1);
		double angle2rad = Math.toRadians(180-keyframe.angle2);
		double angle3rad = Math.toRadians(-keyframe.angle3);
		double angle4rad = Math.toRadians(keyframe.angle4);
		double angle5rad = Math.toRadians(-keyframe.angle5);

		Vector3f nvx = new Vector3f();
		Vector3f nvz = new Vector3f();
		Vector3f vx = new Vector3f();
		Vector3f vz = new Vector3f();

		Vector3f shoulderPosition = new Vector3f(0,0,(float)(SixiRobot.FLOOR_TO_SHOULDER-FLOOR_ADJUST));
		Vector3f shoulderPlaneZ = new Vector3f(0,0,1);
		Vector3f shoulderPlaneX = new Vector3f((float)Math.cos(angle0rad),(float)Math.sin(angle0rad),0);
		Vector3f shoulderPlaneY = new Vector3f();
		shoulderPlaneY.cross(shoulderPlaneX, shoulderPlaneZ);
		shoulderPlaneY.normalize();

		// get rotation at bicep
		nvx.set(shoulderPlaneX);	nvx.scale((float)Math.cos(angle1rad));
		nvz.set(shoulderPlaneZ);	nvz.scale((float)Math.sin(angle1rad));

		Vector3f bicepPlaneY = new Vector3f(shoulderPlaneY);
		Vector3f bicepPlaneZ = new Vector3f(nvx);
		bicepPlaneZ.add(nvz);
		bicepPlaneZ.normalize();
		Vector3f bicepPlaneX = new Vector3f();
		bicepPlaneX.cross(bicepPlaneZ,bicepPlaneY);
		bicepPlaneX.normalize();

		// shoulder to elbow
		vx.set(bicepPlaneX);	vx.scale((float)SixiRobot.SHOULDER_TO_ELBOW_Y);
		vz.set(bicepPlaneZ);	vz.scale((float)SixiRobot.SHOULDER_TO_ELBOW_Z);
		Vector3f shoulderToElbow = new Vector3f();
		shoulderToElbow.add(vx);
		shoulderToElbow.add(vz);
		Vector3f elbowPosition = new Vector3f(shoulderPosition);
		elbowPosition.add(shoulderToElbow);

		if(gl2!=null) {
			gl2.glColor3f(0,0,0);
			
			gl2.glBegin(GL2.GL_LINE_STRIP);
			gl2.glVertex3d(0,0,0);
			gl2.glVertex3d(shoulderPosition.x, shoulderPosition.y, shoulderPosition.z);
			gl2.glEnd();
			
			// shoulder to elbow
			gl2.glPushMatrix();
			gl2.glTranslated(shoulderPosition.x, shoulderPosition.y, shoulderPosition.z);
			gl2.glBegin(GL2.GL_LINE_STRIP);
			gl2.glVertex3d(0,0,0);
			gl2.glVertex3d(vz.x,vz.y,vz.z);
			gl2.glVertex3d(vx.x+vz.x,vx.y+vz.y,vx.z+vz.z);
			gl2.glEnd();
			gl2.glPopMatrix();
		}

		// get the matrix at the elbow
		nvx.set(bicepPlaneZ);	nvx.scale((float)Math.cos(angle2rad));
		nvz.set(bicepPlaneX);	nvz.scale((float)Math.sin(angle2rad));

		Vector3f elbowPlaneY = new Vector3f(shoulderPlaneY);
		Vector3f elbowPlaneZ = new Vector3f(nvx);
		elbowPlaneZ.add(nvz);
		elbowPlaneZ.normalize();
		Vector3f elbowPlaneX = new Vector3f();
		elbowPlaneX.cross(elbowPlaneZ,elbowPlaneY);
		elbowPlaneX.normalize();

		// get elbow to ulna
		vx.set(elbowPlaneX);	vx.scale((float)SixiRobot.ELBOW_TO_ULNA_Y);
		vz.set(elbowPlaneZ);	vz.scale((float)SixiRobot.ELBOW_TO_ULNA_Z);
		Vector3f elbowToUlna = new Vector3f();
		elbowToUlna.add(vx);
		elbowToUlna.add(vz);
		Vector3f ulnaPosition = new Vector3f(elbowPosition);
		ulnaPosition.add(elbowToUlna);

		if(gl2!=null) {
			// elbow to ulna
			gl2.glPushMatrix();
			gl2.glTranslated(elbowPosition.x, elbowPosition.y, elbowPosition.z);
			gl2.glColor3f(0,0,0);
			gl2.glBegin(GL2.GL_LINE_STRIP);
			gl2.glVertex3d(0,0,0);
			gl2.glVertex3d(vx.x,vx.y,vx.z);
			gl2.glVertex3d(vx.x+vz.x,vx.y+vz.y,vx.z+vz.z);
			gl2.glEnd();
			gl2.glPopMatrix();
		}
		
		// get matrix of ulna rotation
		Vector3f ulnaPlaneZ = new Vector3f(nvx);
		ulnaPlaneZ.add(nvz);
		ulnaPlaneZ.normalize();
		Vector3f ulnaPlaneX = new Vector3f();
		vx.set(elbowPlaneX);	vx.scale((float)Math.cos(angle3rad));
		vz.set(elbowPlaneY);	vz.scale((float)Math.sin(angle3rad));
		ulnaPlaneX.add(vx);
		ulnaPlaneX.add(vz);
		ulnaPlaneX.normalize();
		Vector3f ulnaPlaneY = new Vector3f();
		ulnaPlaneY.cross(ulnaPlaneX, ulnaPlaneZ);
		ulnaPlaneY.normalize();

		Vector3f ulnaToWrist = new Vector3f(ulnaPlaneZ);
		ulnaToWrist.scale((float)SixiRobot.ULNA_TO_WRIST_Z);
		Vector3f wristPosition = new Vector3f(ulnaPosition);
		wristPosition.add(ulnaToWrist);
		
		// wrist to finger
		vx.set(ulnaPlaneZ);		vx.scale((float)Math.cos(angle4rad));
		vz.set(ulnaPlaneX);		vz.scale((float)Math.sin(angle4rad));
		Vector3f wristToFingerNormalized = new Vector3f();
		wristToFingerNormalized.add(vx);
		wristToFingerNormalized.add(vz);
		wristToFingerNormalized.normalize();
		Vector3f wristToFinger = new Vector3f(wristToFingerNormalized);
		wristToFinger.scale((float)SixiRobot.WRIST_TO_TOOL_Z);
		
		Vector3f wristPlaneY = new Vector3f(ulnaPlaneY);
		Vector3f wristPlaneZ = new Vector3f(wristToFingerNormalized);
		Vector3f wristPlaneX = new Vector3f();
		wristPlaneX.cross(wristPlaneY,wristPlaneZ);
		wristPlaneX.normalize();
		
		// finger rotation
		Vector3f fingerPlaneY = new Vector3f();
		Vector3f fingerPlaneZ = new Vector3f(wristPlaneZ);
		Vector3f fingerPlaneX = new Vector3f();
		vx.set(wristPlaneX);	vx.scale((float)Math.cos(angle5rad));
		vz.set(wristPlaneY);	vz.scale((float)Math.sin(angle5rad));
		fingerPlaneX.add(vx);
		fingerPlaneX.add(vz);
		fingerPlaneX.normalize();
		fingerPlaneY.cross(fingerPlaneZ, fingerPlaneX);
		Vector3f fingerPosition = new Vector3f(wristPosition);
		fingerPosition.add(wristToFinger);

		// find the UVW rotations for the finger direction
		// I know the fingerPlaneZ is some combination of rotations around globalUp, globalForward, and globalRight.
		// since we roll U, then V, then W... we have to solve backwards.  First find W, then V, then U.
		
		// Project fingerPlaneZ onto the XY plane (newForward) and find the rotation around globalUp
		Vector3f newForward = new Vector3f(fingerPlaneZ);
		Vector3f newRight = new Vector3f(fingerPlaneY);
		float lenW;
		
		// 
		lenW = globalUp.dot(newForward);
		if(Math.abs(lenW)>1-EPSILON) {
			// TODO special case straight along the axis, one way or the other.
		} else {
			Vector3f planeOffsetW = new Vector3f(globalUp);
			planeOffsetW.scale(lenW);
			Vector3f projectedForward = new Vector3f(newForward);
			projectedForward.sub(planeOffsetW);
			projectedForward.normalize();

			double dotX = globalRight.dot(projectedForward); 
			double dotY = globalForward.dot(projectedForward);
			double ikWrad = Math.atan2(dotX, dotY);
			double ikW = Math.toDegrees(MathHelper.capRotationRadians(ikWrad));
			//if(renderMode && showDebug) System.out.print("W"+ikW+"\t");
			keyframe.ikW = (float)ikW;

			// Turn the vectors to remove the effect of W rotation.
			// That will give us better results for V and U.
			newForward = MathHelper.rotateAroundAxis(newForward, globalUp, (float)-ikWrad);
			newRight = MathHelper.rotateAroundAxis(newRight, globalUp, (float)-ikWrad);
		}
		
		// now repeat, solving for V.
		lenW = globalRight.dot(newForward);
		if(Math.abs(lenW)>1-EPSILON) {
			// TODO special case straight along the axis, one way or the other.
		} else {
			Vector3f planeOffsetW = new Vector3f(globalRight);
			planeOffsetW.scale(lenW);
			Vector3f projectedForward = new Vector3f(newForward);
			projectedForward.sub(planeOffsetW);
			projectedForward.normalize();

			double dotX = globalUp.dot(projectedForward); 
			double dotY = globalForward.dot(projectedForward);
			double ikVrad = Math.atan2(-dotX, dotY);
			double ikV = Math.toDegrees(MathHelper.capRotationRadians(ikVrad));
			//if(renderMode && showDebug) System.out.print("V"+ikV+"\t");
			keyframe.ikV = (float)ikV;
			
			// Turn the vectors to remove the effect of V rotation.
			// That will give us better results for U.
			newForward = MathHelper.rotateAroundAxis(newForward, globalRight, (float)-ikVrad);
			newRight = MathHelper.rotateAroundAxis(newRight, globalRight, (float)-ikVrad);
		}
		
		// now repeat, solving for U.  Since newForward started pointing along globalForward, it's probably going to say 1.
		Vector3f projectedForward = new Vector3f(newRight);
		projectedForward.normalize();

		double dotX = globalRight.dot(projectedForward); 
		double dotY = globalUp.dot(projectedForward);
		double ikUrad = Math.atan2(dotX, -dotY);
		double ikU = Math.toDegrees(MathHelper.capRotationRadians(ikUrad));
		//if(renderMode && showDebug) System.out.print("U"+ikU+"\n");
		keyframe.ikU = (float)ikU;
		
		//newForward = MathHelper.rotateAroundAxis(newForward, globalForward, (float)-ikUrad);
		//newRight = MathHelper.rotateAroundAxis(newRight, globalForward, (float)-ikUrad);

		// draw some helpful stuff for solving things.
		if(gl2!=null) {/*
			gl2.glBegin(GL2.GL_LINES);
			gl2.glColor3f(1,1,1);
			gl2.glVertex3d(fingerPosition.x, fingerPosition.y, fingerPosition.z);
			gl2.glVertex3d(	fingerPosition.x+newRight.x,
							fingerPosition.y+newRight.y,
							fingerPosition.z+newRight.z);

			gl2.glColor3f(0.5f,0.5f,0.5f);
			gl2.glVertex3d(fingerPosition.x, fingerPosition.y, fingerPosition.z);
			gl2.glVertex3d(	fingerPosition.x+globalRight.x,
							fingerPosition.y+globalRight.y,
							fingerPosition.z+globalRight.z);

			gl2.glColor3f(0.25f,0.25f,0.25f);
			gl2.glVertex3d(fingerPosition.x, fingerPosition.y, fingerPosition.z);
			gl2.glVertex3d(	fingerPosition.x+globalUp.x,
							fingerPosition.y+globalUp.y,
							fingerPosition.z+globalUp.z);
			gl2.glEnd();*/

			gl2.glBegin(GL2.GL_LINE_STRIP);
			gl2.glColor3f(0,0,0);
			gl2.glVertex3d(ulnaPosition.x, ulnaPosition.y, ulnaPosition.z);
			gl2.glVertex3d(wristPosition.x, wristPosition.y, wristPosition.z);
			gl2.glVertex3d(fingerPosition.x, fingerPosition.y, fingerPosition.z);
			gl2.glEnd();

			drawMatrix(gl2,fingerPosition,globalForward,globalRight,globalUp,5);
			drawMatrix(gl2,shoulderPosition,bicepPlaneX,bicepPlaneY,bicepPlaneZ);
			drawMatrix(gl2,elbowPosition,elbowPlaneX,elbowPlaneY,elbowPlaneZ);
			drawMatrix(gl2,ulnaPosition,ulnaPlaneX,ulnaPlaneY,ulnaPlaneZ);
			drawMatrix(gl2,wristPosition,wristPlaneX,wristPlaneY,wristPlaneZ);
			drawMatrix(gl2,fingerPosition,fingerPlaneX,fingerPlaneY,fingerPlaneZ,3);
		}
		if(renderMode==false) {
			keyframe.shoulder.set(shoulderPosition);
			keyframe.bicep.set(shoulderPosition);
			keyframe.elbow.set(elbowPosition);
			keyframe.wrist.set(wristPosition);
			keyframe.fingerPosition.set(fingerPosition);  // xyz values used in inverse kinematics
			keyframe.fingerRight.set(fingerPlaneX);
			keyframe.fingerForward.set(fingerPlaneZ);
		}
	}
}
