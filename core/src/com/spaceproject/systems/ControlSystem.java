package com.spaceproject.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.spaceproject.SpaceProject;
import com.spaceproject.components.AIComponent;
import com.spaceproject.components.BoundsComponent;
import com.spaceproject.components.CameraFocusComponent;
import com.spaceproject.components.CannonComponent;
import com.spaceproject.components.CharacterComponent;
import com.spaceproject.components.ControlFocusComponent;
import com.spaceproject.components.ControllableComponent;
import com.spaceproject.components.DodgeComponent;
import com.spaceproject.components.PlanetComponent;
import com.spaceproject.components.ScreenTransitionComponent;
import com.spaceproject.components.Sprite3DComponent;
import com.spaceproject.components.TextureComponent;
import com.spaceproject.components.TransformComponent;
import com.spaceproject.components.VehicleComponent;
import com.spaceproject.generation.EntityFactory;
import com.spaceproject.screens.GameScreen;
import com.spaceproject.screens.MyScreenAdapter;
import com.spaceproject.utility.Mappers;
import com.spaceproject.utility.Misc;
import com.spaceproject.utility.MyMath;
import com.spaceproject.utility.SimpleTimer;

public class ControlSystem extends IteratingSystem {

	private ImmutableArray<Entity> vehicles;
	private ImmutableArray<Entity> planets;

	
	public ControlSystem() {
		super(Family.all(ControllableComponent.class, TransformComponent.class).one(CharacterComponent.class, VehicleComponent.class).get());
	}
	
	@Override
	public void addedToEngine(Engine engine) {
		super.addedToEngine(engine);
		vehicles = engine.getEntitiesFor(Family.all(VehicleComponent.class).get());
		planets = engine.getEntitiesFor(Family.all(PlanetComponent.class).get());
	}

	@Override
	protected void processEntity(Entity entity, float delta) {
		
		ControllableComponent control = Mappers.controllable.get(entity);
		
		CharacterComponent character = Mappers.character.get(entity);
		if (character != null) {
			controlCharacter(entity, character, control, delta);
			control.canTransition = false;
		}
		
		VehicleComponent vehicle = Mappers.vehicle.get(entity);
		if (vehicle != null) {
			controlShip(entity, vehicle, control, delta);
		}
		
	}


	//region character controls
	private void controlCharacter(Entity entity, CharacterComponent character, ControllableComponent control, float delta) {
		//players position
		TransformComponent transform = Mappers.transform.get(entity);
		
		//make character face mouse/joystick
		transform.rotation = MathUtils.lerpAngle(transform.rotation, control.angleFacing, 8f*delta);
		
		if (control.moveForward) {
			float walkSpeed = character.walkSpeed * control.movementMultiplier * delta;
			transform.pos.add(MyMath.Vector(transform.rotation, walkSpeed));
		}
		
		if (control.changeVehicle) {			
			enterVehicle(entity, control);
		}
	}
	//endregion



	//region ship controls
	private void controlShip(Entity entity, VehicleComponent vehicle, ControllableComponent control, float delta) {
		TransformComponent transform = Mappers.transform.get(entity);

		//make vehicle face angle from mouse/joystick
		transform.rotation = MathUtils.lerpAngle(transform.rotation, control.angleFacing, 8f*delta);

		if (control.moveForward) {
			accelerate(delta, control, transform, vehicle);
		}

		if (control.moveLeft) {
			dodgeLeft(entity, transform, control);
		}

		if (control.moveRight) {
			dodgeRight(entity, transform, control);
		}

		if (control.moveBack) {
			decelerate(delta, transform);
		}

		//debug force insta-stop(currently affects all vehicles)
		if (Gdx.input.isKeyJustPressed(Keys.X)) transform.velocity.set(0,0);

		barrelRoll(entity, transform);

		//fire cannon / attack
		CannonComponent cannon = Mappers.cannon.get(entity);
		refillAmmo(cannon);//TODO: ammo should refill on all entities regardless of player presence
		if (control.shoot) {
			fireCannon(transform, cannon, entity);
		}


		//transition or take off from planet
		if (GameScreen.inSpace) {
			control.canTransition = canLandOnPlanet(transform.pos);
		} else {
			control.canTransition = true;
		}
		if (control.transition) {
			if (GameScreen.inSpace) {
				landOnPlanet(entity);
			} else {
				takeOffPlanet(entity);
			}
		}


		//exit vehicle
		if (control.changeVehicle) {
			exitVehicle(entity, control);
		}

	}

	private static void accelerate(float delta, ControllableComponent control, TransformComponent transform, VehicleComponent vehicle) {
		//TODO: implement rest of engine behavior
		//float maxSpeedMultiplier? on android touch controls make maxSpeed be relative to finger distance so that finger distance determines how fast to go

		float thrust = vehicle.thrust * control.movementMultiplier * delta;
		transform.velocity.add(MyMath.Vector(transform.rotation, thrust));

		//transform.accel.add(dx,dy);//????

		//cap speed at max. if maxSpeed set to -1 it's infinite(no cap)
		if (vehicle.maxSpeed != VehicleComponent.NOLIMIT)
			transform.velocity.clamp(0, vehicle.maxSpeed);
	}

	private static void decelerate(float delta, TransformComponent transform) {
		int stopThreshold = 20;
		int minBreakingThrust = 10;
		int maxBreakingThrust = 1500;
		if (transform.velocity.len() <= stopThreshold) {
			//completely stop if moving really slowly
			transform.velocity.set(0,0);
		} else {
			//add thrust opposite direction of velocity to slow down ship
			float thrust = transform.velocity.len();//.clamp(minBreakingThrust, maxBreakingThrust);
			float angle = transform.velocity.angle();
			transform.velocity.add(MyMath.Vector(angle, thrust * delta));
		}
	}

	private static void dodgeRight(Entity entity, TransformComponent transform, ControllableComponent control) {
		if (control.timerDodge.canDoEvent() &&  Mappers.dodge.get(entity) == null) {
			control.timerDodge.reset();

			DodgeComponent d = new DodgeComponent();
			d.animationTimer = new SimpleTimer(475, true);
			d.animInterpolation = Interpolation.pow2;//new Interpolation.Pow(2);
			d.revolutions = 1;
			d.distance = 200;
			d.direction = transform.rotation - MathUtils.PI / 2;
			d.dir = DodgeComponent.FlipDir.right;
			entity.add(d);
		}
	}

	private static void dodgeLeft(Entity entity, TransformComponent transform, ControllableComponent control) {
		if (control.timerDodge.canDoEvent() && Mappers.dodge.get(entity) == null) {
			control.timerDodge.reset();

			DodgeComponent d = new DodgeComponent();
			d.animationTimer = new SimpleTimer(475, true);
			d.animInterpolation = Interpolation.pow2;//new Interpolation.Pow(2);
			d.revolutions = 1;
			d.distance = 200;
			d.direction = transform.rotation + MathUtils.PI / 2;
			d.dir = DodgeComponent.FlipDir.right;
			entity.add(d);
		}
	}

	private void barrelRoll(Entity entity, TransformComponent transform) {
		DodgeComponent dodgeComp = Mappers.dodge.get(entity);
		if (dodgeComp != null) {
			//TODO, this use of interpolation seems a little odd, do I need to track distance traveled?
			float interp = dodgeComp.animInterpolation.apply(0, dodgeComp.distance, dodgeComp.animationTimer.ratio());
			float distance = interp - dodgeComp.traveled;
			dodgeComp.traveled += distance;
			transform.pos.add(MyMath.Vector(dodgeComp.direction, distance));
			//System.out.println(dodgeComp.animationTimer.ratio() + ": " + interp + ": " + distance + ": " + dodgeComp.traveled + ": " + (dodgeComp.distance-interp));


			Sprite3DComponent sprite3D = Mappers.sprite3D.get(entity);
			switch (dodgeComp.dir) {
				case left:
					sprite3D.renderable.angle = dodgeComp.animInterpolation.apply(MathUtils.PI2 * dodgeComp.revolutions, 0, dodgeComp.animationTimer.ratio());
					break;
				case right:
					sprite3D.renderable.angle = dodgeComp.animInterpolation.apply(0, MathUtils.PI2 * dodgeComp.revolutions, dodgeComp.animationTimer.ratio());
					break;
			}

			//ensure bounding box follows sprite, ship should be thinner/harder to hit when rolling
			BoundsComponent bounds = Mappers.bounds.get(entity);
			float scaleY = 1-(sprite3D.renderable.angle / MathUtils.PI);//TODO, this is wrong: need to find something that maps like below
			//degrees, scale
			//0   = 1 	-> top
			//45  = 0.5
			//90  = 0
			//135 = 0.5
			//180 = 1 	-> bottom
			//225 = 0.5
			//270 = 0
			//315 = 0.5
			//360 = 1	-> top
			//System.out.println(sprite3D.renderable.angle + ", " + sy);
			bounds.poly.setScale(1, scaleY);


			if (dodgeComp.animationTimer.canDoEvent()) {
				//reset
				sprite3D.renderable.angle = 0;
				bounds.poly.setScale(1,1);

				//end anim
				entity.remove(DodgeComponent.class);
			}
		}
	}

	private void enterVehicle(Entity characterEntity, ControllableComponent control) {
		//action timer
		if (!control.timerVehicle.canDoEvent())
			return;

		control.changeVehicle = false;

		//get all vehicles and check if player is close to one(bounds overlap)
		BoundsComponent playerBounds = Mappers.bounds.get(characterEntity);
		for (Entity vehicle : vehicles) {

			//skip vehicle is occupied
			if (Mappers.vehicle.get(vehicle).driver != null) {
				System.out.println("Vehicle already has a driver!");
				continue;
			}

			//check if character is near a vehicle
			BoundsComponent vehicleBounds = Mappers.bounds.get(vehicle);
			if (playerBounds.poly.getBoundingRectangle().overlaps(vehicleBounds.poly.getBoundingRectangle())) {

				//set references
				Mappers.character.get(characterEntity).vehicle = vehicle;
				Mappers.vehicle.get(vehicle).driver = characterEntity;

				// set focus to vehicle
				if (characterEntity.getComponent(CameraFocusComponent.class) != null) {
					vehicle.add(characterEntity.remove(CameraFocusComponent.class));
					System.out.println("[CameraFocus] " + Misc.myToString(characterEntity) + " -> " + Misc.myToString(vehicle));
				}
				if (characterEntity.getComponent(ControllableComponent.class) != null) {
					vehicle.add(characterEntity.remove(ControllableComponent.class));
					System.out.println("[Controllable] " + Misc.myToString(characterEntity) + " -> " + Misc.myToString(vehicle));
				}
				// move control to vehicle (AI/player)
				if (characterEntity.getComponent(AIComponent.class) != null) {
					vehicle.add(characterEntity.remove(AIComponent.class));
					System.out.println("[AI] " + Misc.myToString(characterEntity) + " -> " + Misc.myToString(vehicle));
				}
				if (characterEntity.getComponent(ControlFocusComponent.class) != null) {
					vehicle.add(characterEntity.remove(ControlFocusComponent.class));
					System.out.println("[ControlFocus] " + Misc.myToString(characterEntity) + " -> " + Misc.myToString(vehicle));
				}


				// remove player from engine
				getEngine().removeEntity(characterEntity);

				//if (entity is controlled by player)
				// zoom out camera, TODO: add pan animation
				MyScreenAdapter.setZoomTarget(1);


				control.timerVehicle.reset();

				return;
			}
		}
	}

	private void exitVehicle(Entity vehicleEntity, ControllableComponent control) {

		//action timer
		if (!control.timerVehicle.canDoEvent())
			return;

		control.changeVehicle = false;

		Entity characterEntity = Mappers.vehicle.get(vehicleEntity).driver;

		// set the player at the position of vehicle
		Vector2 vehiclePosition = Mappers.transform.get(vehicleEntity).pos;
		Vector2 offset = MyMath.Vector(MathUtils.random(360) * MathUtils.degRad, 50);//set player next to vehicle
		Mappers.transform.get(characterEntity).pos.set(vehiclePosition).add(offset);

		//set focus to character
		if (vehicleEntity.getComponent(CameraFocusComponent.class) != null) {
			characterEntity.add(vehicleEntity.remove(CameraFocusComponent.class));
			System.out.println("[CameraFocus] " + Misc.myToString(vehicleEntity) + " -> " + Misc.myToString(characterEntity));
		}
		if (vehicleEntity.getComponent(ControlFocusComponent.class) != null) {
			characterEntity.add(vehicleEntity.remove(ControlFocusComponent.class));
			System.out.println("[ControlFocus] " + Misc.myToString(vehicleEntity) + " -> " + Misc.myToString(characterEntity));
		}

		//move control to character (AI/player)
		if (vehicleEntity.getComponent(AIComponent.class) != null) {
			characterEntity.add(vehicleEntity.remove(AIComponent.class));
			System.out.println("[AI] " + Misc.myToString(vehicleEntity) + " -> " + Misc.myToString(characterEntity));
		}
		if (vehicleEntity.getComponent(ControllableComponent.class) != null) {
			characterEntity.add(vehicleEntity.remove(ControllableComponent.class));
			System.out.println("[Controllable] " + Misc.myToString(vehicleEntity) + " -> " + Misc.myToString(characterEntity));
		}

		// remove references
		Mappers.character.get(characterEntity).vehicle = null;
		Mappers.vehicle.get(vehicleEntity).driver = null;

		// add player back into world
		getEngine().addEntity(characterEntity);

		// zoom in camera
		MyScreenAdapter.setZoomTarget(0.5f);

		control.timerVehicle.reset();
	}
	//endregion



	//region transition
	private static void takeOffPlanet(Entity entity) {
		if (Mappers.screenTrans.get(entity) != null)
			return;

		ScreenTransitionComponent screenTrans = new ScreenTransitionComponent();
		screenTrans.takeOffStage = ScreenTransitionComponent.TakeOffAnimStage.transition;
		screenTrans.timer = new SimpleTimer(SpaceProject.entitycfg.shrinkGrowAnimTime, true);
		screenTrans.animInterpolation = Interpolation.pow2;
		entity.add(screenTrans);

		System.out.println("takeOffPlanet: " + Misc.myToString(entity));
		//Misc.printEntity(entity);
	}

	private void landOnPlanet(Entity entity) {
		if (Mappers.screenTrans.get(entity) != null)
			return;

		Vector2 pos = Mappers.transform.get(entity).pos;
		for (Entity planet : planets) {
			Vector2 planetPos = Mappers.transform.get(planet).pos;
			TextureComponent planetTex = Mappers.texture.get(planet);

			if (pos.dst(planetPos) <= planetTex.texture.getWidth() * 0.5 * planetTex.scale) {

				ScreenTransitionComponent screenTrans = new ScreenTransitionComponent();
				screenTrans.landStage = ScreenTransitionComponent.LandAnimStage.shrink;//begin animation
				screenTrans.planet = planet;
				screenTrans.timer = new SimpleTimer(SpaceProject.entitycfg.shrinkGrowAnimTime, true);
				screenTrans.animInterpolation = Interpolation.sineIn;
				entity.add(screenTrans);

				System.out.println("landOnPlanet: " +  Misc.myToString(entity));
				//Misc.printObjectFields(entity);
				return;
			}
		}
	}
	
	private boolean canLandOnPlanet(Vector2 pos) {
		for (Entity planet : planets) {
			Vector2 planetPos = Mappers.transform.get(planet).pos;
			TextureComponent planetTex = Mappers.texture.get(planet);
			// if player is over planet
			if (pos.dst(planetPos) <= planetTex.texture.getWidth() * 0.5 * planetTex.scale) {
				return true;
			}
		}
		return false;
	}
	//endregion



	//region combat
	private void fireCannon(TransformComponent transform, CannonComponent cannon, Entity owner) {
		/*
		 * Cheat for debug:
		 * fast firing and infinite ammo
		 */
		boolean cheat = false;
		if (cheat) {
			cannon.curAmmo = cannon.maxAmmo;
			cannon.timerFireRate.setLastEvent(0);
		}

		//check if can fire before shooting
		if (!(cannon.curAmmo > 0 && cannon.timerFireRate.canDoEvent()))
			return;

		
		//reset timer if ammo is full, to prevent instant recharge
		if (cannon.curAmmo == cannon.maxAmmo) {
			cannon.timerRechargeRate.reset();
		}		
		
		//create missile
		Vector2 vec = MyMath.Vector(transform.rotation, cannon.velocity).add(transform.velocity);
		getEngine().addEntity(EntityFactory.createMissile(transform, vec, cannon, owner));

		
		//subtract ammo
		--cannon.curAmmo;
		
		//reset timer
		cannon.timerFireRate.reset();
	}

	private static void refillAmmo(CannonComponent cannon) {
		if  (cannon.curAmmo < cannon.maxAmmo && cannon.timerRechargeRate.canDoEvent()) {
			cannon.curAmmo++; //refill ammo
			cannon.timerRechargeRate.reset();
		}
	}
	//endregion
	

}
