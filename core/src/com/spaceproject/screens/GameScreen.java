package com.spaceproject.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.spaceproject.SpaceProject;
import com.spaceproject.components.AIComponent;
import com.spaceproject.components.CameraFocusComponent;
import com.spaceproject.components.ControlFocusComponent;
import com.spaceproject.components.PlanetComponent;
import com.spaceproject.components.ScreenTransitionComponent;
import com.spaceproject.components.SeedComponent;
import com.spaceproject.components.TextureComponent;
import com.spaceproject.components.TransformComponent;
import com.spaceproject.generation.EntityFactory;
import com.spaceproject.systems.AISystem;
import com.spaceproject.systems.BoundsSystem;
import com.spaceproject.systems.CameraSystem;
import com.spaceproject.systems.CollisionSystem;
import com.spaceproject.systems.ControlSystem;
import com.spaceproject.systems.DebugUISystem;
import com.spaceproject.systems.DesktopInputSystem;
import com.spaceproject.systems.ExpireSystem;
import com.spaceproject.systems.HUDSystem;
import com.spaceproject.systems.MobileInputSystem;
import com.spaceproject.systems.MovementSystem;
import com.spaceproject.systems.OrbitSystem;
import com.spaceproject.systems.ScreenTransitionSystem;
import com.spaceproject.systems.SpaceLoadingSystem;
import com.spaceproject.systems.SpaceParallaxSystem;
import com.spaceproject.systems.SpaceRenderingSystem;
import com.spaceproject.systems.WorldRenderingSystem;
import com.spaceproject.systems.WorldWrapSystem;
import com.spaceproject.utility.Mappers;
import com.spaceproject.utility.Misc;

import java.util.concurrent.TimeUnit;

public class GameScreen extends MyScreenAdapter {

	public Engine engine, backgroundEngine;
	public static long gameTimeCurrent, gameTimeStart;


	public static boolean inSpace;
	private static Entity currentPlanet = null;
	//perhaps a game state? eg: dont need to regen the universe each time go to space
	//GameState {
	//	inSpace
	//	universe
	//	currentPlanet
	//}


	ShaderProgram shader = null;


	public GameScreen(boolean inSpace) {
		GameScreen.inSpace = inSpace;

		gameTimeStart = System.nanoTime();


		//playing with shaders
		boolean useShader = false;
		if (useShader) {
			//shader = new ShaderProgram(Gdx.files.internal("shaders/quadRotation.vsh"), Gdx.files.internal("shaders/quadRotation.fsh"));
			//shader = new ShaderProgram(Gdx.files.internal("shaders/passthrough.vsh"), Gdx.files.internal("shaders/passthrough.fsh"));
			shader = new ShaderProgram(Gdx.files.internal("shaders/invert.vsh"), Gdx.files.internal("shaders/invert.fsh"));
			ShaderProgram.pedantic = false;
			System.out.println("Shader compiled: " + shader.isCompiled() + ": " + shader.getLog());
			if (shader.isCompiled())
				batch.setShader(shader);
		}


		// load test default values
		Entity playerTESTSHIP = CreatePlayerShip();
		Entity planet = null;
		//ScreenTransitionComponent transComponent = new ScreenTransitionComponent();

		if (!inSpace && planet == null) {
			planet = EntityFactory.createPlanet(0, new Entity(), 0, false);
			System.out.println("NULL PLANET: Debug world loaded");
			//int a = 1/0;// throw new Exception("");
		}

		
		if (inSpace) {
			initSpace(playerTESTSHIP);
		} else {
			initWorld(playerTESTSHIP, planet);
		}

		//transitioningEntities = engine.getEntitiesFor(Family.all(ScreenTransitionComponent.class).get());

	}

	private Entity CreatePlayerShip() {
		Entity player = EntityFactory.createCharacter(0, 0);
		Entity playerTESTSHIP = EntityFactory.createShip3(0, 0, 0, player);
		playerTESTSHIP.add(new CameraFocusComponent());
		playerTESTSHIP.add(new ControlFocusComponent());
		return playerTESTSHIP;
	}

	private void initSpace(Entity transitioningEntity) {
		System.out.println("==========SPACE==========");
		inSpace = true;
		currentPlanet = null;
		
		
		// engine to handle all entities and components
		engine = new Engine();



		//===============SYSTEMS===============
		//input
		if (Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS) {
			engine.addSystem(new MobileInputSystem());
		} else {
			engine.addSystem(new DesktopInputSystem());
		}
		engine.addSystem(new AISystem());

		//loading
		//TODO: SpaceParallaxSystem & SpaceLoadingSystem are a source of jigger/jumping while moving, caused by loading/unloading textures.
		engine.addSystem(new SpaceLoadingSystem());
		engine.addSystem(new SpaceParallaxSystem());

		//logic
		engine.addSystem(new ScreenTransitionSystem());
		engine.addSystem(new ControlSystem());
		engine.addSystem(new ExpireSystem(1));
		engine.addSystem(new OrbitSystem());
		engine.addSystem(new MovementSystem());
		engine.addSystem(new BoundsSystem());
		engine.addSystem(new CollisionSystem());


		//rendering
		engine.addSystem(new CameraSystem());
		engine.addSystem(new SpaceRenderingSystem());
		engine.addSystem(new HUDSystem());
		engine.addSystem(new DebugUISystem());






		//===============ENTITIES===============
		//test ships
		engine.addEntity(EntityFactory.createShip3(-200, 400));
		engine.addEntity(EntityFactory.createShip3(-300, 400));
		engine.addEntity(EntityFactory.createShip3(-400, 400));
		engine.addEntity(EntityFactory.createShip3(-600, 400));

		
		//add player
		engine.addEntity(transitioningEntity);


		Entity aiTest = EntityFactory.createCharacterAI(0, 400);
		Mappers.AI.get(aiTest).state = AIComponent.testState.dumbwander;
		//aiTest.add(ship.remove(CameraFocusComponent.class));//test cam focus on AI
		engine.addEntity(aiTest);

		Entity aiTest2 = EntityFactory.createCharacterAI(0, 600);
		Mappers.AI.get(aiTest2).state = AIComponent.testState.idle;
		engine.addEntity(aiTest2);

		Entity aiTest3 = EntityFactory.createCharacterAI(0, 800);
		Mappers.AI.get(aiTest3).state = AIComponent.testState.landOnPlanet;
		engine.addEntity(aiTest3);


	}
	
	
	private void initWorld(Entity transitioningEntity, Entity planet) {
		System.out.println("==========WORLD==========");
		inSpace = false;
		currentPlanet = planet;

		Misc.printObjectFields(planet.getComponent(SeedComponent.class));
		Misc.printObjectFields(planet.getComponent(PlanetComponent.class));
		//Misc.printEntity(transitionComponent.transitioningEntity);



		// engine to handle all entities and components
		engine = new Engine();


		// ===============SYSTEMS===============
		// input
		if (Gdx.app.getType() == ApplicationType.Android || Gdx.app.getType() == ApplicationType.iOS) {
			engine.addSystem(new MobileInputSystem());
		} else {
			engine.addSystem(new DesktopInputSystem());
		}
		engine.addSystem(new AISystem());

		// loading

		// logic
		engine.addSystem(new ScreenTransitionSystem());
		engine.addSystem(new ControlSystem());
		engine.addSystem(new ExpireSystem(1));
		engine.addSystem(new MovementSystem());
		engine.addSystem(new WorldWrapSystem(planet.getComponent(PlanetComponent.class).mapSize));
		engine.addSystem(new BoundsSystem());
		engine.addSystem(new CollisionSystem());

		// rendering
		engine.addSystem(new CameraSystem());
		engine.addSystem(new WorldRenderingSystem(planet));
		engine.addSystem(new HUDSystem());
		engine.addSystem(new DebugUISystem());



		// ===============ENTITIES===============
		// add player
		Entity ship = transitioningEntity;
		int position = planet.getComponent(PlanetComponent.class).mapSize * SpaceProject.tileSize / 2;//set  position to middle of planet
		ship.getComponent(TransformComponent.class).pos.set(position, position);
		engine.addEntity(ship);

		

		// test ships near player
		engine.addEntity(EntityFactory.createShip3(position + 100, position + 600));
		engine.addEntity(EntityFactory.createShip3(position - 100, position + 600));

		Entity aiTest = EntityFactory.createCharacterAI(position, position + 50);
		Mappers.AI.get(aiTest).state = AIComponent.testState.dumbwander;
		engine.addEntity(aiTest);

		Entity aiTest2 = EntityFactory.createCharacterAI(position, position - 500);
		Mappers.AI.get(aiTest2).state = AIComponent.testState.takeOffPlanet;
		engine.addEntity(aiTest2);


	}

	ImmutableArray<Entity> transitioningEntities;
	@Override
	public void render(float delta) {
		super.render(delta);

		// update engine
		gameTimeCurrent = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - gameTimeStart);
		engine.update(delta);


		CheckTransition();


		if (Gdx.input.isButtonPressed(Input.Buttons.MIDDLE)) {
			//todo: move into hudsystem & check button from config
			if (HUDSystem.drawMap != HUDSystem.MapState.off) {
				HUDSystem.spaceMapScale = 500;
			}
		}

		if (Gdx.input.isKeyJustPressed(Keys.U)) {
			//debug
			Misc.printEntities(engine);
		}

		if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
			game.setScreen(new MainMenuScreen(game));
		}
	}

	private void CheckTransition() {
		boolean transition = false;
		Entity transEntity = null;
		if (transitioningEntities == null) {
			transitioningEntities = engine.getEntitiesFor(Family.all(ScreenTransitionComponent.class).get());
		}

		//System.out.println(transitioningEntities.size());
		for (Entity e : transitioningEntities) {
			//System.out.println("transitioning: " + Misc.myToString(e));
			ScreenTransitionComponent screenTrans = Mappers.screenTrans.get(e);
			if (screenTrans.doTransition) {
				screenTrans.doTransition = false;



				if (e.getComponent(ControlFocusComponent.class) != null) {
					transition = true;
					transEntity = e;
					//Mappers.screenTrans.get(e).landStage = ScreenTransitionComponent.LandAnimStage.pause;
				}

				if (Mappers.AI.get(e) != null) {
					System.out.println("REMOVING: " + Misc.myToString(e));
					engine.removeEntity(e);
						/*
						if (Mappers.persist.get(e)) {
							System.out.println("MOVED to background engine: " + Misc.myToString(e));
							backgroundEngine.addEntity(e);
						}*/
				}

				if (inSpace) {
					//screenTrans.landStage = ScreenTransitionComponent.LandAnimStage.pause;
					screenTrans.landStage = screenTrans.landStage.next();
				} else {
					//screenTrans.takeOffStage = ScreenTransitionComponent.TakeOffAnimStage.sync;
					screenTrans.takeOffStage = screenTrans.takeOffStage.next();
				}
			}
		}


		if (transition) {
			engine.removeAllEntities();
			//engine.removeAllSystems();?

			if (inSpace) {
				initWorld(transEntity, Mappers.screenTrans.get(transEntity).planet);
			} else {
				Mappers.screenTrans.get(transEntity).planet = currentPlanet;
				initSpace(transEntity);

			}
			transitioningEntities = null;//engine.getEntitiesFor(Family.all(ScreenTransitionComponent.class).get());
			//engine.addEntityListener(Family.all(ScreenTransitionComponent.class).get(),this);
			/*
			for (Entity relevantEntity : backgroundEngine.getEntities()) {
				//if landing on planet, and relevantEntity is on planet, add to engine, remove from backgroundEngine
				//if going to space, and relevantEntity in space, add to engine, remove from backgroundEngine
			}*/

			//Misc.printEntities(engine);
			//Misc.printSystems(engine);
		}
	}

	@Override
	public boolean scrolled(int amount) {
		if (HUDSystem.drawMap == HUDSystem.MapState.full) {
			HUDSystem.spaceMapScale += amount*20;
			return false;
		} else {
			return super.scrolled(amount);
		}

	}

	@Override
	public void dispose() {
		System.out.println("Disposing: " + this.getClass().getSimpleName());

		// clean up after self
		// dispose of spritebatches and textures
		for (EntitySystem sys : engine.getSystems()) {
			if (sys instanceof Disposable)
				((Disposable) sys).dispose();
		}

		//TODO: use entity listener instead
		//Family family = Family.all(TextureComponent.class).get();
		//engine.addEntityListener(family, listener);
		//github.com/libgdx/ashley/wiki/How-to-use-Ashley#entity-events
		for (Entity ents : engine.getEntitiesFor(Family.all(TextureComponent.class).get())) {
			TextureComponent tex = ents.getComponent(TextureComponent.class);
			if (tex != null)
				tex.texture.dispose();
		}

		engine.removeAllEntities();//THIS FIXES IT!
		engine = null;


		//System.gc();//not good practice, just testing

		//super.dispose();
	}

	@Override
	public void hide() {
		// dispose();
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}


}
