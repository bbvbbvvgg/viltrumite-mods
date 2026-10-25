package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

class GuiEntitySilhouetteTest {
   @Test void modelVerticesBecomeFlatWhiteWithoutTextureOrLight(){var recorder=new Consumer();var white=GuiEntitySilhouette.white(recorder,.5F);white.vertex(1,2,3).color(10,20,30,40).uv(.2F,.5F).overlayCoords(0,0).uv2(0,0).normal(0,0,1).endVertex();assertEquals(255,recorder.r);assertEquals(255,recorder.g);assertEquals(255,recorder.b);assertEquals(128,recorder.a);assertEquals(1,recorder.vertices);assertEquals(0,recorder.uvs);assertEquals(0,recorder.lights);assertEquals(0,recorder.normals);}
   static class Consumer implements VertexConsumer {int r,g,b,a,vertices,uvs,lights,normals;public VertexConsumer vertex(double x,double y,double z){return this;}public VertexConsumer color(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}public VertexConsumer uv(float u,float v){uvs++;return this;}public VertexConsumer overlayCoords(int u,int v){lights++;return this;}public VertexConsumer uv2(int u,int v){lights++;return this;}public VertexConsumer normal(float x,float y,float z){normals++;return this;}public void endVertex(){vertices++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}}
}
