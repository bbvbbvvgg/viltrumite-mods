package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A positional constraint on the existing two-bone arm, not a pose/animation engine.
 * The handle centre is measured from the native handle cube (y=-6.5..0.7).
 * Damped least-squares solves the actual rendered attachment chain, including parent, elbow,
 * item anchor and scale, rather than assuming that a shoulder angle places the palm on the hilt.
 */
final class ReinhardGrip {
   static final Vector3f HANDLE=new Vector3f(0,-3.5F/16,0);
   private static final float EPS=.001F, TOLERANCE=.002F/16;
   private ReinhardGrip() {}
   static Matrix4f hand(BakedGeoModel model) { return hand(model,false); }
   static Matrix4f hand(BakedGeoModel model,boolean left) {
      PoseStack stack=new PoseStack();
      // Centre of the black glove cube in the native archive, not vanilla's y-down item offset.
      // BakedGeoModel mirrors native X. The old Rz(180)*Rx(-90) item transform put
      // the supposed grip ABOVE the shoulder; numerical alignment hid that mistake.
      GeoBone wrist=model.getBone(left?"left_hand":"right_hand");
      ReinhardRenderer.boneFrame(stack,wrist);
      // Handle traverses the closed fist across its width, not lengthwise through
      // the forearm. The independent wrist carries this orientation, not the sleeve.
      stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
      stack.scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE*ReinhardSword.LENGTH_SCALE,ReinhardSword.SWORD_SCALE);
      stack.translate(-HANDLE.x,-HANDLE.y,-HANDLE.z);
      return new Matrix4f(stack.last().pose());
   }
   /** Camera arm uses the same measured palm, but its blade points forward in
    * geo space before PlayerGeoLayer's vanilla conversion, not sideways across HUD. */
   static Matrix4f cameraHand(BakedGeoModel model) {
      PoseStack stack=new PoseStack();ReinhardRenderer.boneFrame(stack,model.getBone("right_hand"));
      stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
      stack.scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE*ReinhardSword.LENGTH_SCALE,ReinhardSword.SWORD_SCALE);
      stack.translate(-HANDLE.x,-HANDLE.y,-HANDLE.z);
      return new Matrix4f(stack.last().pose());
   }
   static Vector3f point(BakedGeoModel model) { return point(model,false); }
   static Vector3f point(BakedGeoModel model,boolean left) { return hand(model,left).transformPosition(new Vector3f(HANDLE)); }
   static float solve(BakedGeoModel model,Matrix4f mount,float weight) { return solve(model,mount,weight,false); }
   /** Rest the closed glove ON the hilt. A resting palm is not a sword wielding grip:
    * keep the wrist aligned with the forearm instead of forcing the blade's roll. */
   static void rest(BakedGeoModel model,Matrix4f mount,float weight) {
      support(model,mount,weight,0);
   }
   static void support(BakedGeoModel model,Matrix4f mount,float weight,float gripWeight) {
      Matrix4f target=new Matrix4f(mount).m31(mount.m31()+(1-gripWeight)*1.3F/16);
      solve(model,target,weight,true,false);
      if(gripWeight>0)orientWrist(model,mount,weight*gripWeight,true);
   }
   static float solve(BakedGeoModel model,Matrix4f mount,float weight,boolean left) {return solve(model,mount,weight,left,true);}
   /** Pronation belongs to the forearm, not a dislocated 90-degree wrist.
    * Rotate about elbow-to-palm: the palm and elbow positions stay unchanged. */
   static void paired(BakedGeoModel model,Matrix4f mount,float weight,boolean left) {
      solve(model,mount,weight,left,false);
      GeoBone lower=model.getBone(left?"left_forearm":"right_forearm");
      GeoBone wrist=model.getBone(left?"left_hand":"right_hand");
      PoseStack stack=new PoseStack();ReinhardRenderer.boneFrame(stack,lower);
      Vector3f localAxis=new Vector3f(wrist.pivotX-lower.pivotX,wrist.pivotY-lower.pivotY,wrist.pivotZ-lower.pivotZ).normalize();
      Vector3f axis=stack.last().pose().transformDirection(new Vector3f(localAxis)).normalize();
      Vector3f across=stack.last().pose().transformDirection(new Vector3f(1,0,0)).normalize();
      Vector3f desired=mount.transformDirection(new Vector3f(0,1,0)).normalize();
      across.sub(new Vector3f(axis).mul(across.dot(axis)));
      desired.sub(new Vector3f(axis).mul(desired.dot(axis)));
      if(across.lengthSquared()>1E-7F && desired.lengthSquared()>1E-7F) {
         across.normalize();desired.normalize();if(across.dot(desired)<0)desired.negate();
         float turn=(float)Math.atan2(axis.dot(new Vector3f(across).cross(desired)),across.dot(desired));
         var q=new org.joml.Quaternionf().rotationZYX(lower.rotZ,lower.rotY,lower.rotX)
            .rotateAxis(turn*weight,localAxis.x,localAxis.y,localAxis.z);
         Vector3f e=euler(q);lower.rotX=e.x;lower.rotY=e.y;lower.rotZ=e.z;
      }
      // Keep the palm continuous with the sleeve; forearm twist did the work.
      wrist.rotX*=1-weight;wrist.rotY*=1-weight;wrist.rotZ*=1-weight;
   }
   private static float solve(BakedGeoModel model,Matrix4f mount,float weight,boolean left,boolean alignWrist) {
      GeoBone upper=model.getBone(left?"left_arm":ReinhardAssets.RIGHT_ARM),lower=model.getBone(left?"left_forearm":"right_forearm");
      if(upper==null || lower==null || weight<=0) return 0;
      Vector3f target=mount.transformPosition(new Vector3f(HANDLE));
      float[] initial={upper.rotX,upper.rotY,upper.rotZ,lower.rotX,lower.rotY,lower.rotZ};
      float[] angles=initial.clone();
      seedFrontElbow(model,upper,lower,target,angles,left);
      set(upper,lower,angles);
      for(int iteration=0;iteration<32;iteration++) {
         Vector3f p=point(model,left), error=new Vector3f(target).sub(p);
         if(error.length()<TOLERANCE) break;
         Vector3f[] j=new Vector3f[6];
         for(int k=0;k<6;k++) {
            angles[k]+=EPS;set(upper,lower,angles);
            j[k]=point(model,left).sub(p).div(EPS);
            angles[k]-=EPS;set(upper,lower,angles);
         }
         // J J^T + lambda I: invert a 3x3 matrix, not the singular 6-DOF normal matrix.
         Matrix3f normal=new Matrix3f().zero();
         for(Vector3f v:j) normal.add(new Matrix3f(v.x*v.x,v.y*v.x,v.z*v.x,v.x*v.y,v.y*v.y,v.z*v.y,v.x*v.z,v.y*v.z,v.z*v.z));
         normal.m00(normal.m00()+1E-5F).m11(normal.m11()+1E-5F).m22(normal.m22()+1E-5F);
         Vector3f correction=normal.invert().transform(error);
         for(int k=0;k<6;k++) angles[k]+=Math.max(-.25F,Math.min(.25F,j[k].dot(correction)));
         set(upper,lower,angles);
      }
      float residual=point(model,left).distance(target)*16;
      for(int k=0;k<6;k++) angles[k]=initial[k]+(float)Math.atan2(Math.sin(angles[k]-initial[k]),Math.cos(angles[k]-initial[k]))*weight;
      set(upper,lower,angles);
      if(alignWrist)orientWrist(model,mount,weight,left);
      return residual;
   }
   /** Two-segment geometric seed with an explicit forward elbow pole. Position-only
    * least squares has infinitely many solutions, including an elbow behind the neck.
    * This chooses the anatomical half-plane before the small numeric correction. */
   private static void seedFrontElbow(BakedGeoModel model,GeoBone upper,GeoBone lower,Vector3f target,float[] angles,boolean left) {
      PoseStack parent=new PoseStack(),shoulder=new PoseStack();
      if(upper.parent!=null) {
         ReinhardRenderer.boneFrame(parent,upper.parent);
         parent.translate(-upper.parent.pivotX/16,-upper.parent.pivotY/16,-upper.parent.pivotZ/16);
      }
      ReinhardRenderer.boneFrame(shoulder,upper);
      Vector3f root=shoulder.last().pose().getTranslation(new Vector3f());
      Vector3f restUpper=new Vector3f(lower.pivotX-upper.pivotX,lower.pivotY-upper.pivotY,lower.pivotZ-upper.pivotZ).div(16);
      Vector3f restLower=new Vector3f((left?-5.3281F:5.3281F)-lower.pivotX,12.6993F-lower.pivotY,-lower.pivotZ).div(16);
      float a=restUpper.length(),b=restLower.length();Vector3f delta=new Vector3f(target).sub(root);
      float distance=Math.max(.001F,Math.min(delta.length(),a+b-.001F));Vector3f axis=delta.normalize();
      Vector3f pole=parent.last().pose().transformDirection(new Vector3f(left?-.7F:.7F,-2,-1));
      pole.sub(new Vector3f(axis).mul(pole.dot(axis)));
      if(pole.lengthSquared()<1E-6F) {pole=parent.last().pose().transformDirection(new Vector3f(0,-1,0));pole.sub(new Vector3f(axis).mul(pole.dot(axis)));}
      pole.normalize();float along=(distance*distance+a*a-b*b)/(2*distance);
      Vector3f elbow=new Vector3f(root).add(new Vector3f(axis).mul(along)).add(pole.mul((float)Math.sqrt(Math.max(0,a*a-along*along))));
      Vector3f upperGoal=new Matrix4f(parent.last().pose()).invert().transformDirection(new Vector3f(elbow).sub(root));
      Vector3f e=euler(new org.joml.Quaternionf().rotationTo(restUpper,upperGoal));
      angles[0]=e.x;angles[1]=e.y;angles[2]=e.z;set(upper,lower,angles);
      PoseStack arm=new PoseStack();ReinhardRenderer.boneFrame(arm,upper);
      Vector3f lowerGoal=new Matrix4f(arm.last().pose()).invert().transformDirection(new Vector3f(target).sub(elbow));
      e=euler(new org.joml.Quaternionf().rotationTo(restLower,lowerGoal));
      angles[3]=e.x;angles[4]=e.y;angles[5]=e.z;
   }
   private static void orientWrist(BakedGeoModel model,Matrix4f weapon,float weight,boolean left) {
      GeoBone wrist=model.getBone(left?"left_hand":"right_hand");
      if(wrist==null)return;
      PoseStack parent=new PoseStack();ReinhardRenderer.boneFrame(parent,wrist.parent);
      org.joml.Quaternionf base=parent.last().pose().getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
      org.joml.Quaternionf desired=weapon.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize()
         .rotateZ((float)Math.PI/2);
      desired=base.invert().mul(desired);
      // The sleeve must not end in a dislocated 90-degree wrist. Grip position
      // owns contact; weapon roll is not a command to flip a resting palm.
      if(desired.w<0)desired.mul(-1);
      float angle=2*(float)Math.acos(Math.min(1,desired.w));
      float limit=(float)Math.toRadians(35);
      if(angle>limit)desired=new org.joml.Quaternionf().slerp(desired,limit/angle);
      Vector3f e=euler(new org.joml.Quaternionf().rotationZYX(wrist.rotZ,wrist.rotY,wrist.rotX).slerp(desired,weight));
      wrist.rotX=e.x;wrist.rotY=e.y;wrist.rotZ=e.z;
   }
   /** Exact inverse of Rz*Ry*Rx used by AnimRenderer. JOML 1.10.5's
    * getEulerAnglesZYX has an X denominator that does not invert this chain. */
   static Vector3f euler(org.joml.Quaternionf q) {
      Matrix3f r=new Matrix3f().rotation(q);
      float c=(float)Math.sqrt(r.m12()*r.m12()+r.m22()*r.m22());
      if(c<1E-6F)return new Vector3f(0,(float)Math.atan2(-r.m02(),c),(float)Math.atan2(-r.m10(),r.m11()));
      return new Vector3f((float)Math.atan2(r.m12(),r.m22()),(float)Math.atan2(-r.m02(),c),(float)Math.atan2(r.m01(),r.m00()));
   }
   private static void set(GeoBone a,GeoBone b,float[] v){a.rotX=v[0];a.rotY=v[1];a.rotZ=v[2];b.rotX=v[3];b.rotY=v[4];b.rotZ=v[5];}
}
