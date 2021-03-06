package com.spaceproject.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.spaceproject.SpaceProject;
import com.spaceproject.components.ControlFocusComponent;
import com.spaceproject.components.ControllableComponent;
import com.spaceproject.screens.MyScreenAdapter;
import com.spaceproject.utility.Mappers;
import com.spaceproject.utility.MyMath;

public class DesktopInputSystem extends EntitySystem implements InputProcessor {

	private ImmutableArray<Entity> players;
	
	@Override
	public void addedToEngine(com.badlogic.ashley.core.Engine engine) {
		players = engine.getEntitiesFor(Family.all(ControlFocusComponent.class, ControllableComponent.class).get());
	}	
	
	@Override
	public void update(float delta) {	
		cameraControls(delta);
	}

	private boolean playerControls(int keycode, boolean keyDown) {
		if (players.size() == 0) 
			return false;

		boolean handled = false;

		ControllableComponent control = Mappers.controllable.get(players.first());
		
		//movement
		control.movementMultiplier = 1; // set multiplier to full power because a key switch is on or off
		if (keycode == SpaceProject.keycfg.forward) {
			control.moveForward = keyDown;
			handled = true;
		}
		if (keycode == SpaceProject.keycfg.right) {
			control.moveRight = keyDown;
			handled = true;
		}
		if (keycode == SpaceProject.keycfg.left) {
			control.moveLeft = keyDown;
			handled = true;
		}
		if (keycode == SpaceProject.keycfg.back) {
			control.moveBack = keyDown;
			handled = true;
		}

		if (keycode == SpaceProject.keycfg.changeVehicle) {
			control.changeVehicle = keyDown;
			handled = true;
		}
		if (keycode == SpaceProject.keycfg.land) {
			control.transition = keyDown;
			handled = true;
		}

		return handled;
	}

	private boolean playerFace(int x, int y) {
		if (players.size() == 0)
			return false;

		ControllableComponent control = Mappers.controllable.get(players.first());

		float angle = MyMath.angleTo(x, Gdx.graphics.getHeight() - y,
				Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight()/2);
		control.angleFacing = angle;
		return true;
	}


	private static void cameraControls(float delta) {
		//zoom test
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.zoomSpace))  {
			if (MyScreenAdapter.cam.zoom >= 10f) {
				MyScreenAdapter.setZoomTarget(60);
			} else {
				MyScreenAdapter.setZoomTarget(10);
			}
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.resetZoom)) {
			MyScreenAdapter.setZoomTarget(1);
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.zoomCharacter)) {
			MyScreenAdapter.setZoomTarget(0.1f);
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.zoomOut)) {
			MyScreenAdapter.setZoomTarget(MyScreenAdapter.cam.zoom + 0.001f);
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.zoomIn)) {
			MyScreenAdapter.setZoomTarget(MyScreenAdapter.cam.zoom - 0.001f);
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.rotateRight)) {
			MyScreenAdapter.cam.rotate(5f * delta);
		}
		if (Gdx.input.isKeyPressed(SpaceProject.keycfg.rotateLeft)) {
			MyScreenAdapter.cam.rotate(-5f * delta);
		}
		

	}


	@Override
	public boolean keyDown(int keycode) {
		return playerControls(keycode, true);
	}


	@Override
	public boolean keyUp(int keycode) {
		return playerControls(keycode, false);
	}

	@Override
	public boolean keyTyped(char character) {
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		if (players.size() != 0) {
			ControllableComponent control = Mappers.controllable.get(players.first());
			control.shoot = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if (players.size() != 0) {
			ControllableComponent control = Mappers.controllable.get(players.first());
			control.shoot = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		return playerFace(screenX, screenY);
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		return playerFace(screenX, screenY);
	}

	@Override
	public boolean scrolled(int amount) {
		return false;
	}
}
