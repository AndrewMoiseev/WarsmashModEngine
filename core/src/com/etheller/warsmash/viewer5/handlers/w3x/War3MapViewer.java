package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.common.LoadGenericCallback;
import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.MpqDataSource;
import com.etheller.warsmash.datasources.SubdirDataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.doo.War3MapDoo;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.unitsdoo.War3MapUnitsDoo;
import com.etheller.warsmash.parsers.w3x.w3e.War3MapW3e;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.manager.MutableObjectData;
import com.etheller.warsmash.units.manager.MutableObjectData.MutableGameObject;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.MappedData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WorldEditStrings;
import com.etheller.warsmash.viewer5.CanvasProvider;
import com.etheller.warsmash.viewer5.GenericResource;
import com.etheller.warsmash.viewer5.Grid;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain.Splat;

import mpq.MPQArchive;
import mpq.MPQException;

public class War3MapViewer extends ModelViewer {
	private static final War3ID UNIT_FILE = War3ID.fromString("umdl");
	private static final War3ID UBER_SPLAT = War3ID.fromString("uubs");
	private static final War3ID UNIT_SHADOW = War3ID.fromString("ushu");
	private static final War3ID UNIT_SHADOW_X = War3ID.fromString("ushx");
	private static final War3ID UNIT_SHADOW_Y = War3ID.fromString("ushy");
	private static final War3ID UNIT_SHADOW_W = War3ID.fromString("ushw");
	private static final War3ID UNIT_SHADOW_H = War3ID.fromString("ushh");
	private static final War3ID BUILDING_SHADOW = War3ID.fromString("ushb");
	public static final War3ID UNIT_SELECT_SCALE = War3ID.fromString("ussc");
	private static final War3ID UNIT_SELECT_HEIGHT = War3ID.fromString("uslz");
	private static final War3ID UNIT_SOUNDSET = War3ID.fromString("usnd");
	private static final War3ID ITEM_FILE = War3ID.fromString("ifil");
	private static final War3ID sloc = War3ID.fromString("sloc");
	private static final LoadGenericCallback stringDataCallback = new StringDataCallbackImplementation();
	private static final float[] rayHeap = new float[6];
	private static final Vector2 mousePosHeap = new Vector2();
	private static final Vector3 normalHeap = new Vector3();
	private static final Vector3 intersectionHeap = new Vector3();
	public static final StreamDataCallbackImplementation streamDataCallback = new StreamDataCallbackImplementation();

	public PathSolver wc3PathSolver = PathSolver.DEFAULT;
	public SolverParams solverParams = new SolverParams();
	public Scene worldScene;
	public boolean anyReady;
	public boolean terrainCliffsAndWaterLoaded;
	public MappedData terrainData = new MappedData();
	public MappedData cliffTypesData = new MappedData();
	public MappedData waterData = new MappedData();
	public boolean terrainReady;
	public boolean cliffsReady;
	public boolean doodadsAndDestructiblesLoaded;
	public MappedData doodadsData = new MappedData();
	public MappedData doodadMetaData = new MappedData();
	public MappedData destructableMetaData = new MappedData();
	public List<Doodad> doodads = new ArrayList<>();
	public List<Object> terrainDoodads = new ArrayList<>();
	public boolean doodadsReady;
	public boolean unitsAndItemsLoaded;
	public MappedData unitsData = new MappedData();
	public MappedData unitMetaData = new MappedData();
	public List<Unit> units = new ArrayList<>();
	public boolean unitsReady;
	public War3Map mapMpq;
	public PathSolver mapPathSolver = PathSolver.DEFAULT;

	private final DataSource gameDataSource;

	public Terrain terrain;
	public int renderPathing = 0;
	public int renderLighting = 0;

	public List<SplatModel> selModels = new ArrayList<>();
	public List<Unit> selected = new ArrayList<>();
	private DataTable unitAckSoundsTable;
	private MdxComplexInstance confirmationInstance;

	public War3MapViewer(final DataSource dataSource, final CanvasProvider canvas) {
		super(dataSource, canvas);
		this.gameDataSource = dataSource;

		final WebGL webGL = this.webGL;

		this.addHandler(new MdxHandler());

		this.wc3PathSolver = PathSolver.DEFAULT;

		this.worldScene = this.addScene();
	}

	public void loadSLKs(final WorldEditStrings worldEditStrings) throws IOException {
		final GenericResource terrain = this.loadMapGeneric("TerrainArt\\Terrain.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource cliffTypes = this.loadMapGeneric("TerrainArt\\CliffTypes.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource water = this.loadMapGeneric("TerrainArt\\Water.slk", FetchDataTypeName.SLK,
				stringDataCallback);

		// == when loaded, which is always in our system ==
		this.terrainCliffsAndWaterLoaded = true;
		this.terrainData.load(terrain.data.toString());
		this.cliffTypesData.load(cliffTypes.data.toString());
		this.waterData.load(water.data.toString());
		// emit terrain loaded??

		final GenericResource doodads = this.loadMapGeneric("Doodads\\Doodads.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource doodadMetaData = this.loadMapGeneric("Doodads\\DoodadMetaData.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource destructableData = this.loadMapGeneric("Units\\DestructableData.slk",
				FetchDataTypeName.SLK, stringDataCallback);
		final GenericResource destructableMetaData = this.loadMapGeneric("Units\\DestructableMetaData.slk",
				FetchDataTypeName.SLK, stringDataCallback);

		// == when loaded, which is always in our system ==
		this.doodadsAndDestructiblesLoaded = true;
		this.doodadsData.load(doodads.data.toString());
		this.doodadMetaData.load(doodadMetaData.data.toString());
		this.doodadsData.load(destructableData.data.toString());
		this.destructableMetaData.load(destructableData.data.toString());
		// emit doodads loaded

		final GenericResource unitData = this.loadMapGeneric("Units\\UnitData.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource unitUi = this.loadMapGeneric("Units\\unitUI.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource itemData = this.loadMapGeneric("Units\\ItemData.slk", FetchDataTypeName.SLK,
				stringDataCallback);
		final GenericResource unitMetaData = this.loadMapGeneric("Units\\UnitMetaData.slk", FetchDataTypeName.SLK,
				stringDataCallback);

		// == when loaded, which is always in our system ==
		this.unitsAndItemsLoaded = true;
		this.unitsData.load(unitData.data.toString());
		this.unitsData.load(unitUi.data.toString());
		this.unitsData.load(itemData.data.toString());
		this.unitMetaData.load(unitMetaData.data.toString());
		// emit loaded

		this.unitAckSoundsTable = new DataTable(worldEditStrings);
		try (InputStream terrainSlkStream = this.dataSource.getResourceAsStream("UI\\SoundInfo\\UnitAckSounds.slk")) {
			this.unitAckSoundsTable.readSLK(terrainSlkStream);
		}

	}

	public GenericResource loadMapGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback) {
		if (this.mapMpq == null) {
			return loadGeneric(path, dataType, callback);
		}
		else {
			return loadGeneric(path, dataType, callback, this.dataSource);
		}
	}

	public void loadMap(final String mapFilePath) throws IOException {
		final War3Map war3Map = new War3Map(this.gameDataSource, mapFilePath);

		this.mapMpq = war3Map;

		final PathSolver wc3PathSolver = this.wc3PathSolver;

		char tileset = 'A';

		final War3MapW3i w3iFile = this.mapMpq.readMapInformation();

		tileset = w3iFile.getTileset();

		DataSource tilesetSource;
		try {
			// Slightly complex. Here's the theory:
			// 1.) Copy map into RAM
			// 2.) Setup a Data Source that will read assets
			// from either the map or the game, giving the map priority.
			SeekableByteChannel sbc;
			final CompoundDataSource compoundDataSource = war3Map.getCompoundDataSource();
			try (InputStream mapStream = compoundDataSource.getResourceAsStream(tileset + ".mpq")) {
				if (mapStream == null) {
					tilesetSource = new CompoundDataSource(Arrays.asList(compoundDataSource,
							new SubdirDataSource(compoundDataSource, tileset + ".mpq/")));
				}
				else {
					final byte[] mapData = IOUtils.toByteArray(mapStream);
					sbc = new SeekableInMemoryByteChannel(mapData);
					final DataSource internalMpqContentsDataSource = new MpqDataSource(new MPQArchive(sbc), sbc);
					tilesetSource = new CompoundDataSource(
							Arrays.asList(compoundDataSource, internalMpqContentsDataSource));
				}
			}
			catch (final IOException exc) {
				tilesetSource = new CompoundDataSource(
						Arrays.asList(compoundDataSource, new SubdirDataSource(compoundDataSource, tileset + ".mpq/")));
			}
		}
		catch (final MPQException e) {
			throw new RuntimeException(e);
		}
		setDataSource(tilesetSource);
		final WorldEditStrings worldEditStrings = new WorldEditStrings(this.dataSource);
		loadSLKs(worldEditStrings);

		this.solverParams.tileset = Character.toLowerCase(tileset);

		final War3MapW3e terrainData = this.mapMpq.readEnvironment();

		this.terrain = new Terrain(terrainData, w3iFile, this.webGL, this.dataSource, worldEditStrings, this);

		final float[] centerOffset = terrainData.getCenterOffset();
		final int[] mapSize = terrainData.getMapSize();

		this.terrainReady = true;
		this.anyReady = true;
		this.cliffsReady = true;

		// Override the grid based on the map.
		this.worldScene.grid = new Grid(centerOffset[0], centerOffset[1], (mapSize[0] * 128) - 128,
				(mapSize[1] * 128) - 128, 16 * 128, 16 * 128);

		final MdxModel confirmation = (MdxModel) load("UI\\Feedback\\Confirmation\\Confirmation.mdx",
				PathSolver.DEFAULT, null);
		this.confirmationInstance = (MdxComplexInstance) confirmation.addInstance();
		this.confirmationInstance.setSequenceLoopMode(0);
		this.confirmationInstance.setSequence(0);
		this.confirmationInstance.setScene(this.worldScene);

		if (this.terrainCliffsAndWaterLoaded) {
			this.loadTerrainCliffsAndWater(terrainData);
		}
		else {
			throw new IllegalStateException("transcription of JS has not loaded a map and has no JS async promises");
		}

		final Warcraft3MapObjectData modifications = this.mapMpq.readModifications();

		if (this.doodadsAndDestructiblesLoaded) {
			this.loadDoodadsAndDestructibles(modifications);
		}
		else {
			throw new IllegalStateException("transcription of JS has not loaded a map and has no JS async promises");
		}

		if (this.unitsAndItemsLoaded) {
			this.loadUnitsAndItems(modifications);
		}
		else {
			throw new IllegalStateException("transcription of JS has not loaded a map and has no JS async promises");
		}
	}

	private void loadTerrainCliffsAndWater(final War3MapW3e w3e) {

	}

	private void loadDoodadsAndDestructibles(final Warcraft3MapObjectData modifications) throws IOException {
		final War3MapDoo dooFile = this.mapMpq.readDoodads();

		this.applyModificationFile(this.doodadsData, this.doodadMetaData, modifications.getDoodads(),
				WorldEditorDataType.DOODADS);
		this.applyModificationFile(this.doodadsData, this.destructableMetaData, modifications.getDestructibles(),
				WorldEditorDataType.DESTRUCTIBLES);

		final War3MapDoo doo = this.mapMpq.readDoodads();

		for (final com.etheller.warsmash.parsers.w3x.doo.Doodad doodad : doo.getDoodads()) {
			WorldEditorDataType type = WorldEditorDataType.DOODADS;
			MutableGameObject row = modifications.getDoodads().get(doodad.getId());
			if (row == null) {
				row = modifications.getDestructibles().get(doodad.getId());
				type = WorldEditorDataType.DESTRUCTIBLES;
			}
			String file = row.readSLKTag("file");
			final int numVar = row.readSLKTagInt("numVar");

			if (file.endsWith(".mdx") || file.endsWith(".mdl")) {
				file = file.substring(0, file.length() - 4);
			}

			String fileVar = file;

			file += ".mdx";

			if (numVar > 1) {
				fileVar += Math.min(doodad.getVariation(), numVar - 1);
			}

			fileVar += ".mdx";

			// First see if the model is local.
			// Doodads referring to local models may have invalid variations, so if the
			// variation doesn't exist, try without a variation.

			String path;
			if (this.mapMpq.has(fileVar)) {
				path = fileVar;
			}
			else {
				path = file;
			}
			MdxModel model;
			if (this.mapMpq.has(path)) {
				model = (MdxModel) this.load(path, this.mapPathSolver, this.solverParams);
			}
			else {
				model = (MdxModel) this.load(fileVar, this.mapPathSolver, this.solverParams);
			}

			this.doodads.add(new Doodad(this, model, row, doodad, type));
		}

		// Cliff/Terrain doodads.
		for (final com.etheller.warsmash.parsers.w3x.doo.TerrainDoodad doodad : doo.getTerrainDoodads()) {
			final MutableGameObject row = modifications.getDoodads().get(doodad.getId());
			String file = row.readSLKTag("file");
			if (file.toLowerCase().endsWith(".mdl")) {
				file = file.substring(0, file.length() - 4);
			}
			if (!file.toLowerCase().endsWith(".mdx")) {
				file += ".mdx";
			}
			final MdxModel model = (MdxModel) this.load(file, this.mapPathSolver, this.solverParams);

			this.terrainDoodads.add(new TerrainDoodad(this, model, row, doodad));
		}

		this.doodadsReady = true;
		this.anyReady = true;
	}

	private void applyModificationFile(final MappedData doodadsData2, final MappedData doodadMetaData2,
			final MutableObjectData destructibles, final WorldEditorDataType dataType) {
		// TODO condense ported MappedData from Ghostwolf and MutableObjectData from
		// Retera

	}

	private void loadUnitsAndItems(final Warcraft3MapObjectData modifications) throws IOException {
		final War3Map mpq = this.mapMpq;

		final War3MapUnitsDoo dooFile = mpq.readUnits();

		final Map<String, UnitSoundset> soundsetNameToSoundset = new HashMap<>();

		// Collect the units and items data.
		UnitSoundset soundset = null;
		for (final com.etheller.warsmash.parsers.w3x.unitsdoo.Unit unit : dooFile.getUnits()) {
			MutableGameObject row = null;
			String path = null;

			// Hardcoded?
			WorldEditorDataType type = null;
			if (sloc.equals(unit.getId())) {
//				path = "Objects\\StartLocation\\StartLocation.mdx";
				type = null; /// ??????
			}
			else {
				row = modifications.getUnits().get(unit.getId());
				if (row == null) {
					row = modifications.getItems().get(unit.getId());
					if (row != null) {
						type = WorldEditorDataType.ITEM;
						path = row.getFieldAsString(ITEM_FILE, 0);

						if (path.toLowerCase().endsWith(".mdl") || path.toLowerCase().endsWith(".mdx")) {
							path = path.substring(0, path.length() - 4);
						}

						path += ".mdx";
					}
				}
				else {
					type = WorldEditorDataType.UNITS;
					path = row.getFieldAsString(UNIT_FILE, 0);

					if (path.toLowerCase().endsWith(".mdl") || path.toLowerCase().endsWith(".mdx")) {
						path = path.substring(0, path.length() - 4);
					}
					if (row.readSLKTagInt("fileVerFlags") == 2) {
						path += "_V1";
					}

					path += ".mdx";

					final String uberSplat = row.getFieldAsString(UBER_SPLAT, 0);
					if (uberSplat != null) {
						final Element uberSplatInfo = this.terrain.uberSplatTable.get(uberSplat);
						if (uberSplatInfo != null) {
							final String texturePath = uberSplatInfo.getField("Dir") + "\\"
									+ uberSplatInfo.getField("file") + ".blp";
							if (!this.terrain.splats.containsKey(texturePath)) {
								this.terrain.splats.put(texturePath, new Splat());
							}
							final float x = unit.getLocation()[0];
							final float y = unit.getLocation()[1];
							final float s = uberSplatInfo.getFieldFloatValue("Scale");
							this.terrain.splats.get(texturePath).locations
									.add(new float[] { x - s, y - s, x + s, y + s, 1 });
						}
					}

					final String unitShadow = row.getFieldAsString(UNIT_SHADOW, 0);
					if ((unitShadow != null) && !"_".equals(unitShadow)) {
						final String texture = "ReplaceableTextures\\Shadows\\" + unitShadow + ".blp";
						final float shadowX = row.getFieldAsFloat(UNIT_SHADOW_X, 0);
						final float shadowY = row.getFieldAsFloat(UNIT_SHADOW_Y, 0);
						final float shadowWidth = row.getFieldAsFloat(UNIT_SHADOW_W, 0);
						final float shadowHeight = row.getFieldAsFloat(UNIT_SHADOW_H, 0);
						if (!this.terrain.splats.containsKey(texture)) {
							final Splat splat = new Splat();
							splat.opacity = 0.5f;
							this.terrain.splats.put(texture, splat);
						}
						final float x = unit.getLocation()[0] - shadowX;
						final float y = unit.getLocation()[1] - shadowY;
						this.terrain.splats.get(texture).locations
								.add(new float[] { x, y, x + shadowWidth, y + shadowHeight, 3 });
					}

					final String buildingShadow = row.getFieldAsString(BUILDING_SHADOW, 0);
					if ((buildingShadow != null) && !"_".equals(buildingShadow)) {
						this.terrain.addShadow(buildingShadow, unit.getLocation()[0], unit.getLocation()[1]);
					}

					final String soundName = row.getFieldAsString(UNIT_SOUNDSET, 0);
					UnitSoundset unitSoundset = soundsetNameToSoundset.get(soundName);
					if (unitSoundset == null) {
						unitSoundset = new UnitSoundset(this.dataSource, this.unitAckSoundsTable, soundName);
						soundsetNameToSoundset.put(soundName, unitSoundset);
					}
					soundset = unitSoundset;
				}
			}

			if (path != null) {
				final MdxModel model = (MdxModel) this.load(path, this.mapPathSolver, this.solverParams);
				MdxModel portraitModel;
				final String portraitPath = path.substring(0, path.length() - 4) + "_portrait.mdx";
				if (this.dataSource.has(portraitPath)) {
					portraitModel = (MdxModel) this.load(portraitPath, this.mapPathSolver, this.solverParams);
				}
				else {
					portraitModel = model;
				}
				this.units.add(new Unit(this, model, row, unit, type, soundset, portraitModel));
			}
			else {
				System.err.println("Unknown unit ID: " + unit.getId());
			}
		}

		this.terrain.loadSplats();

		this.unitsReady = true;
		this.anyReady = true;
	}

	@Override
	public void update() {
		if (this.anyReady) {
			this.terrain.update();

			super.update();

			final List<ModelInstance> instances = this.worldScene.instances;

			for (final ModelInstance instance : instances) {
				if ((instance instanceof MdxComplexInstance) && (instance != this.confirmationInstance)) {
					final MdxComplexInstance mdxComplexInstance = (MdxComplexInstance) instance;
					if (mdxComplexInstance.sequenceEnded || (mdxComplexInstance.sequence == -1)) {
						StandSequence.randomStandSequence(mdxComplexInstance);
					}
				}
			}
			if (this.confirmationInstance.sequenceEnded) {
				this.confirmationInstance.hide();
			}
		}
	}

	@Override
	public void render() {
		if (this.anyReady) {
			final Scene worldScene = this.worldScene;

			startFrame();
			worldScene.startFrame();
			this.terrain.renderGround();
			this.terrain.renderCliffs();
			worldScene.renderOpaque();
			this.terrain.renderUberSplats();
			this.terrain.renderWater();
			worldScene.renderTranslucent();

			final List<Scene> scenes = this.scenes;
			for (final Scene scene : scenes) {
				if (scene != worldScene) {
					scene.startFrame();
					scene.renderOpaque();
					scene.renderTranslucent();
				}
			}
		}
	}

	public void deselect() {
		if (!this.selModels.isEmpty()) {
			for (final SplatModel model : this.selModels) {
				this.terrain.removeSplatBatchModel(model);
			}
			this.selModels.clear();
		}
		this.selected.clear();
	}

	public void doSelectUnit(final List<Unit> units) {
		deselect();
		if (units.isEmpty()) {
			return;
		}

		final Map<String, Terrain.Splat> splats = new HashMap<String, Terrain.Splat>();
		for (final Unit unit : units) {
			if (unit.row != null) {
				if (unit.radius > 0) {
					final float radius = unit.radius;
					String path;
					// TODO these radius values must be read from UI\MiscData.txt instead
					if (radius < 100) {
						path = "ReplaceableTextures\\Selection\\SelectionCircleSmall.blp";
					}
					else if (radius < 300) {
						path = "ReplaceableTextures\\Selection\\SelectionCircleMed.blp";
					}
					else {
						path = "ReplaceableTextures\\Selection\\SelectionCircleLarge.blp";
					}
					if (!splats.containsKey(path)) {
						splats.put(path, new Splat());
					}
					final float x = unit.location[0];
					final float y = unit.location[1];
					final float z = unit.row.getFieldAsFloat(UNIT_SELECT_HEIGHT, 0);
					splats.get(path).locations
							.add(new float[] { x - radius, y - radius, x + radius, y + radius, z + 5 });
				}
			}
		}
		this.selModels.clear();
		for (final Map.Entry<String, Terrain.Splat> entry : splats.entrySet()) {
			final String path = entry.getKey();
			final Splat locations = entry.getValue();
			final SplatModel model = new SplatModel(Gdx.gl30, (Texture) load(path, PathSolver.DEFAULT, null),
					locations.locations, this.terrain.centerOffset);
			model.color[0] = 0;
			model.color[1] = 1;
			model.color[2] = 0;
			model.color[3] = 1;
			this.selModels.add(model);
			this.terrain.addSplatBatchModel(model);
		}
	}

	public void getClickLocation(final Vector3 out, final int screenX, final int screenY) {
		final float[] ray = rayHeap;
		mousePosHeap.set(screenX, screenY);
		this.worldScene.camera.screenToWorldRay(ray, mousePosHeap);
		final Ray gdxRay = new Ray(new Vector3(ray[0], ray[1], ray[2]),
				new Vector3(ray[3] - ray[0], ray[4] - ray[1], ray[5] - ray[2]));
		Terrain.intersectRayTriangles(gdxRay, this.terrain.softwareGroundMesh.vertices,
				this.terrain.softwareGroundMesh.indices, 3, out);
	}

	public void showConfirmation(final Vector3 position, final float red, final float green, final float blue) {
		this.confirmationInstance.show();
		this.confirmationInstance.setSequence(0);
		this.confirmationInstance.setLocation(position);
		this.confirmationInstance.vertexColor[0] = red;
		this.confirmationInstance.vertexColor[1] = green;
		this.confirmationInstance.vertexColor[2] = blue;
	}

	public List<Unit> selectUnit(final float x, final float y, final boolean toggle) {
		final float[] ray = rayHeap;
		mousePosHeap.set(x, y);
		this.worldScene.camera.screenToWorldRay(ray, mousePosHeap);
		final Vector3 dir = normalHeap;
		dir.x = ray[3] - ray[0];
		dir.y = ray[4] - ray[1];
		dir.z = ray[5] - ray[2];
		dir.nor();
		// TODO good performance, do not create vectors on every check
		final Vector3 eMid = new Vector3();
		final Vector3 eSize = new Vector3();
		final Vector3 rDir = new Vector3();

		Unit entity = null;
		float entDist = 1e6f;

		for (final Unit unit : this.units) {
			final float radius = unit.radius;
			final float[] location = unit.location;
			final MdxComplexInstance instance = unit.instance;
			eMid.set(0, 0, radius / 2);
			eSize.set(radius, radius, radius);

			eMid.add(location[0], location[1], location[2]);
			eMid.sub(ray[0], ray[1], ray[2]);
			eMid.scl(1 / eSize.x, 1 / eSize.y, 1 / eSize.z);
			rDir.x = dir.x / eSize.x;
			rDir.y = dir.y / eSize.y;
			rDir.z = dir.z / eSize.z;
			final float dlen = rDir.len2();
			final float dp = Math.max(0, rDir.dot(eMid)) / dlen;
			if (dp > entDist) {
				continue;
			}
			rDir.scl(dp);
			if (rDir.dst2(eMid) < 1.0) {
				entity = unit;
				entDist = dp;
			}
		}
		List<Unit> sel;
		if (entity != null) {
			if (toggle) {
				sel = new ArrayList<>(this.selected);
				final int idx = sel.indexOf(entity);
				if (idx >= 0) {
					sel.remove(idx);
				}
				else {
					sel.add(entity);
				}
			}
			else {
				sel = Arrays.asList(entity);
			}
		}
		else {
			sel = Collections.emptyList();
		}
		this.doSelectUnit(sel);
		return sel;
	}

	private static final class MappedDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			final StringBuilder stringBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(data, "utf-8"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append("\n");
				}
			}
			catch (final UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
			return new MappedData(stringBuilder.toString());
		}
	}

	private static final class StringDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			if (data == null) {
				System.err.println("data null");
			}
			final StringBuilder stringBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(data, "utf-8"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append("\n");
				}
			}
			catch (final UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
			return stringBuilder.toString();
		}
	}

	private static final class StreamDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			return data;
		}
	}

	public static final class SolverParams {
		public char tileset;
		public boolean reforged;
		public boolean hd;
	}

	public static final class CliffInfo {
		public List<float[]> locations = new ArrayList<>();
		public List<Integer> textures = new ArrayList<>();
	}

	private static final int MAXIMUM_ACCEPTED = 1 << 30;

	/**
	 * Returns a power of two size for the given target capacity.
	 */
	private static final int pow2GreaterThan(final int capacity) {
		int numElements = capacity - 1;
		numElements |= numElements >>> 1;
		numElements |= numElements >>> 2;
		numElements |= numElements >>> 4;
		numElements |= numElements >>> 8;
		numElements |= numElements >>> 16;
		return (numElements < 0) ? 1 : (numElements >= MAXIMUM_ACCEPTED) ? MAXIMUM_ACCEPTED : numElements + 1;
	}

}