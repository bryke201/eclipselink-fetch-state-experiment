package com.test.eclipselink.jpa.fetch_state;

import static org.junit.Assert.assertTrue;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUtil;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.eclipse.persistence.config.QueryHints;
import org.eclipse.persistence.internal.jpa.EntityManagerFactoryImpl;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.queries.FetchGroup;
import org.eclipse.persistence.queries.FetchGroupTracker;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.test.eclipselink.jpa.fetch_state.entities.KeyMappingCollectionAssoc;
import com.test.eclipselink.jpa.fetch_state.entities.OwnedAssoc;
import com.test.eclipselink.jpa.fetch_state.entities.OwningAssoc;
import com.test.eclipselink.jpa.fetch_state.entities.ReferenceMappingCollectionAssoc;
import com.test.eclipselink.jpa.fetch_state.entities.RootEntity;

/**
 * 
 * @author http://briaguy.blogspot.com/
 *
 * Knowing if attributes and associations of a JPA entity are initialized has its uses, among others:
 * 		-for when there is need to define custom TraversableResolvers for partially fetched entity validation
 * 		-creating custom optimization APIs using a JPA provider's internals
 * 
 * For some providers, this can be a trivial matter (heck, even JPA provides it optionally in the spec), 
 * though I find that the more optimization options there are, even more complex are the rules in place. 
 * 
 * Unfortunately, Eclipselink is one of those providers with considerable complexity, even as it implements the
 * method defined by the JPA spec (PersistenceUtil.isLoaded methods).
 * 
 * This test set explores how Eclipselink behaves when different ways of identifying entity initialization state are used.
 * 
 * This test set works with the following setup: 
 *      -the Eclipselink version used is 2.5.2
 *      -RootEntity is composed of two basic fields and four associations, where each association depicts a common way of mapping 
 *      -OwnedAssoc has a LAZY basic field relevant to demonstrations in this test set 
 *      -each ToOne association is declared LAZY, while ToMany/collection associations are left as they are already LAZY by default 
 *      -the Eclipselink L2 cache is turned off (see persistence.xml property "eclipselink.cache.shared.default")
 *         	so each context made is fresh
 *      -batching is enabled via the persistence.xml property "eclipselink.jdbc.batch-writing" value="Oracle-JDBC"
 *         	to show a good optimization option; this is responsible for the batch INSERT and UPDATE statements
 *         	executed when merging the collection associations of RootEntity
 *      -the full set (by default) of woven capabilities of Eclipselink are used; using only a subset can introduce
 *         	many more complications, which would dramatically increase the things to be observed, so it was avoided
 *
 */

public class TestFetchState {

	private static EntityManagerFactory emf;

	@BeforeClass
	public static void initializeEnv() {

		RootEntity rootEntity = new RootEntity();
		rootEntity.setId(1L);
		rootEntity.setData1("Root:1L:Data1");
		rootEntity.setData2("Root:1L:Data2");

		OwnedAssoc ownedAssoc = new OwnedAssoc(1L, "OwnedAssoc:1L:Data1", "OwnedAssoc:1L:Data2");

		OwningAssoc owningAssoc = new OwningAssoc(1L, "OwningAssoc:1L:Data1", "OwningAssoc:1L:Data2");

		emf = Persistence.createEntityManagerFactory("test");
		EntityManager em = createEM();
		em.getTransaction().begin();
		rootEntity = em.merge(rootEntity);
		rootEntity.setOwnedAssoc(em.merge(ownedAssoc));

		owningAssoc.setOwnedParent(rootEntity);
		owningAssoc = em.merge(owningAssoc);
		rootEntity.setOwningAssoc(owningAssoc);

		rootEntity.addKeyCollectionAssoc(
				new KeyMappingCollectionAssoc(1L, "KeyCollAssoc:1L:Data1", "KeyCollAssoc:1L:Data2"));
		rootEntity.addKeyCollectionAssoc(
				new KeyMappingCollectionAssoc(2L, "KeyCollAssoc:2L:Data1", "KeyCollAssoc:2L:Data2"));

		rootEntity.addRefCollectionAssoc(
				new ReferenceMappingCollectionAssoc(1L, "RefCollAssoc:1L:Data1", "RefCollAssoc:1L:Data2"));
		rootEntity.addRefCollectionAssoc(
				new ReferenceMappingCollectionAssoc(2L, "RefCollAssoc:2L:Data1", "RefCollAssoc:2L:Data2"));

		em.getTransaction().commit();
		em.close();
	}

	@AfterClass
	public static void tearDown() {
		emf.close();
	}

	private static EntityManager createEM() {
		return emf.createEntityManager();
	}

	private static void assertEntityIsWoven(Object entity) {
		assertTrue(
				"The entity is not configured for tracking fetch groups. Perhaps weaving was not enabled for this entity or at all.",
				entity instanceof FetchGroupTracker);
	}

	/**
	 * 
	 * This first part aims to build (or refresh) a quick background on how Eclipselink entities manage partial fetching.
	 * 
	 * The following tests describe the presence of default FetchGroups when entities are queried for without explicit
	 * FetchGroup hints.
	 * 
	 * A FetchGroup is how Eclipselink internally defines which attributes and associations to initialize when an entity is requested.
	 * It can specify deeply by handling strings with tokens separated by a dot (".") that denotes traversal through an association to
	 * be able to declare an attribute or further association of that association.
	 * 
	 * Note: Id and Version fields are always included in fetching, regardless of their presence in a FetchGroup
	 * Note: default FetchGroups are one of the many things managed by an entity's ClassDescriptor, managed by the Eclipselink Session
	 */

	@Test
	public void ENTITY_WITH_ONLY_LAZY_ASSOCS_has_no_default_FetchGroup() {
		EntityManager em = createEM();
		RootEntity rootEnt = findRootEntityById(1L, em);

		assertEntityIsWoven(rootEnt);
		assertTrue("A woven entity without LAZY Basic mappings should not have a default FetchGroup.",
				((FetchGroupTracker) rootEnt)._persistence_getFetchGroup() == null);
		em.close();
	}

	private RootEntity findRootEntityById(Long id, EntityManager em) {
		return em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class).setParameter("id", 1L)
				.getSingleResult();
	}

	@Test
	public void ENTITY_WITH_LAZY_BASIC_has_default_FetchGroup() {
		EntityManager em = createEM();
		OwnedAssoc assocEnt = findOwnedAssocById(1L, em);

		assertEntityIsWoven(assocEnt);
		FetchGroup fg = ((FetchGroupTracker) assocEnt)._persistence_getFetchGroup();
		assertTrue("A woven entity with LAZY Basic mappings should have a default FetchGroup.", fg != null);
		assertTrue("OwnedAssoc entity default FetchGroup should not contain 'data1'", !fg.containsAttribute("data1"));
		assertTrue("OwnedAssoc entity default FetchGroup should contain 'data2'", fg.containsAttribute("data2"));
		em.close();
	}

	private OwnedAssoc findOwnedAssocById(Long id, EntityManager em) {
		return em.createQuery("SELECT o FROM OwnedAssoc o WHERE o.id = :id", OwnedAssoc.class).setParameter("id", 1L)
				.getSingleResult();
	}

	@Test
	//The difference here is that the OwnedAssoc entity was taken from the root, though it still gets queried for separately.
	public void ENTITY_WITH_LAZY_BASIC_from_root_has_default_FetchGroup_ROOT_DOES_NOT() {

		EntityManager em = createEM();
		RootEntity rootEnt = findRootEntityById(1L, em);

		assertEntityIsWoven(rootEnt);
		assertTrue("A woven entity without LAZY Basic mappings should not have a default FetchGroup.",
				((FetchGroupTracker) rootEnt)._persistence_getFetchGroup() == null);

		OwnedAssoc assocEnt = rootEnt.getOwnedAssoc();
		assertEntityIsWoven(assocEnt);
		FetchGroup fg = ((FetchGroupTracker) assocEnt)._persistence_getFetchGroup();
		assertTrue("A woven entity with LAZY Basic mappings should have a default FetchGroup.", fg != null);
		assertTrue("OwnedAssoc entity default FetchGroup should not contain 'data1'", !fg.containsAttribute("data1"));
		assertTrue("OwnedAssoc entity default FetchGroup should contain 'data2'", fg.containsAttribute("data2"));

		em.close();
	}

	/**
	 * 
	 * In essence, default FetchGroups only exist for entities that have specified a basic mapping to be LAZY by default configuration
	 * (where default configuration means having been taken from an ORM XML annotation).
	 * 
	 * 
	 * As mentioned, JPA provides the PersistenceUnitUtil interface for implementation by providers as to provide a way for users
	 * to find out whether an attribute or association is loaded or not.
	 * 
	 * Built from the knowledge from the previous tests, this next set describes the behavior of the Eclipselink implementation of
	 * PersistenceUnitUtil, which is part of the JPA spec for determining entity load state.
	 * 
	 */

	@Test
	public void ENTITY_WITH_LAZY_BASIC_behavior() {
		EntityManager em = createEM();
		OwnedAssoc ownedAssoc = findOwnedAssocById(1L, em);

		PersistenceUtil pUtil = Persistence.getPersistenceUtil();

		assertTrue("default/EAGER basic fields should return true.", pUtil.isLoaded(ownedAssoc, "data2"));
		assertTrue("LAZY basic fields should return false.", !pUtil.isLoaded(ownedAssoc, "data1"));
		assertTrue(
				"Because the default (explicit annotation) setting was followed, where only data1 should be LAZY, checking the "
						+ "whole entity with isLoaded should return true",
				pUtil.isLoaded(ownedAssoc));

		em.close();
	}

	/**
	 * 
	 * As of JPA 2.1.0, javax.persistence.Persistence.getPersistenceUtil() returns an instance of a static inner
	 * class that implements the javax.persistence.PersistenceUtil interface.
	 * 
	 * This inner class relies on the provider implementation of javax.persistence.spi.ProviderUtil's 
	 * "isLoadedWithoutReference(Object entity, String attributeName)" and "isLoadedWithReference(Object entity, String attributeName)",
	 * which Eclipselink also implements in the org.eclipse.persistence.jpa.PersistenceProvider class.
	 * 
	 * The ProviderUtil specifies a return type of javax.persitence.spi.LoadState, an enum with three choices: LOADED, NOT_LOADED, and
	 * UNKNOWN.
	 * 
	 * Note that Within the code of the PersistenceUtil inner class of Persistence, a return value
	 * of LoadState.UNKNOWN leads to the isLoaded() method returning "true". Though it could have been decided that false is returned,
	 * such a decision is actually quite trivial as the providers can predetermine and decide accordingly,
	 * based on their implementation (hey, they know better!).
	 * 
	 * ============javax.persistence.spi.ProviderUtil
	 * 
	 * Let us move to javax.persistence.spi.ProviderUtil's isLoaded methods.
	 * 
	 * Quoting the comment appearing before the isLoadedWithoutReference and isLoadedWithReference methods, 
	 * 
	 *  	"If the provider determines that the entity has been provided by itself and that the state of the specified 
	 *  	 attribute has been loaded, this method returns LoadState.LOADED. 
	 *  
	 *  	 If the provider determines that the entity has been provided by itself and that either entity attributes 
	 *   	 with FetchType.EAGER have not been loaded or that the state of the specified attribute has not been loaded, 
	 *       this methods returns LoadState.NOT_LOADED.
	 *        
	 *  	 If a provider cannot determine the load state, this method returns LoadState.UNKNOWN,"
	 * 
	 * it seems that, from the second condition, it is possible that the check could return "false" regardless of which attribute is queried
	 * for if an attribute that was eager by default was not initialized (possible through custom FetchGroups).
	 * 
	 * The ProviderUtil methods isLoadedWithoutReference and isLoadedWithReference is disambiguated in a further comment by mentioning that
	 * "isLoadedWithReference" is PERMITTED to obtain a reference to the attribute value. In essence, the spec PERMITS initialization
	 * of the attribute, BUT Eclipselink DOES NOT DO THIS. After all, we were only asking if it was loaded.
	 * 
	 * The final method of ProviderUtil, isLoaded (which is used in the isLoaded(Object entity) method of PersistenceUtil), performs
	 * a whole entity check. This method also has a comment:
	 * 
	 * 		"If the provider determines that the entity has been provided by itself and that the state of all attributes for 
	 * 		 which FetchType.EAGER has been specified have been loaded, this method returns LoadState.LOADED.
	 * 
	 *  	 If the provider determines that the entity has been provided by itself and that not all attributes with 
	 *  	 FetchType.EAGER have been loaded, this method returns LoadState.NOT_LOADED. 
	 *  
	 *  	 If the provider cannot determine if the entity has been provided by itself, this method returns LoadState.UNKNOWN."
	 *  
	 * Because it has no specific attributes to worry about, this method is simpler: it specifies that LOADED should be returned if
	 * everything in the default setting set as EAGER is initialized. The comment for this method describes behavior similar to the second
	 * condition in the comment of isLoadedWith/outReference.
	 * 
	 * If these methods behave such that the entire entity is considered to be NOT_LOADED (via dyanmically modifying the FetchGroup so that
	 * it differs from the default configuration) could potentially disregard checking specific attributes, 
	 * then the utility might have been quite misleading.
	 * 
	 */

	/**
	 * Following from the rules previously mentioned, the following test should follow the standard, assuming that following entity
	 * defaults would produce predictable results.
	 * 
	 * This time, we use RootEntity, where only associations were made lazy, much like the other entities.
	 * 
	 * The difference between OwnedAssoc, which has a LAZY BASIC attribute, from the other entities is that OwnedAssoc
	 * carries its own DEFAULT FETCH GROUP, where the others would return null.
	 */

	@Test
	public void ENTITY_WITH_ONLY_LAZY_ASSOCS_behavior() {
		EntityManager em = createEM();
		RootEntity rootEnt = findRootEntityById(1L, em);

		PersistenceUtil pUtil = Persistence.getPersistenceUtil();
		assertTrue(
				"RootEntity, with the associations default (explicit annotations) set to LAZY, should be found fully loaded",
				pUtil.isLoaded(rootEnt));

		//default basic fields should be loaded
		assertAttLoaded_PersUtil(rootEnt, "data1");
		assertAttLoaded_PersUtil(rootEnt, "data2");
		//uninitialized LAZY associations should not be loaded
		assertAttNotLoaded_PersUtil(rootEnt, "ownedAssoc");
		assertAttNotLoaded_PersUtil(rootEnt, "owningAssoc");
		assertAttNotLoaded_PersUtil(rootEnt, "keyCollectionAssoc");
		assertAttNotLoaded_PersUtil(rootEnt, "refCollectionAssoc");

		em.close();
	}

	private static void assertAttLoaded_PersUtil(Object entity, String attName) {
		assertTrue("Attribute/Association " + attName + " of [" + entity.getClass().getSimpleName()
				+ "] should be loaded.", Persistence.getPersistenceUtil().isLoaded(entity, attName));
	}

	private static void assertAttNotLoaded_PersUtil(Object entity, String attName) {
		assertTrue("Attribute/Association " + attName + " of [" + entity.getClass().getSimpleName()
				+ "] should NOT be loaded.", !Persistence.getPersistenceUtil().isLoaded(entity, attName));
	}

	/**
	 * So far, so good. However, the succeeding sets will change that.
	 * 
	 * Currently, as mentioned, it seems that the PersistenceUtil methods work properly when dealing with entities that use their
	 * DEFAULT fetch groups.
	 * 
	 * What if we introduce custom fetch groups, as we normally would if we want to dynamically configure fetching?
	 * 
	 * Let's do just that.
	 * 
	 */

	@Test
	public void CUSTOM_FetchGroup_WITH_ONLY_BASIC_ATT() {
		EntityManager em = createEM();
		TypedQuery<RootEntity> query = em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class);
		query.setParameter("id", 1L);

		//a FetchGroup that only fetches "data1" (and id, implicitly)
		applyFetchGroupToQuery(query, "data1");

		RootEntity ent = query.getSingleResult();
		PersistenceUtil pUtil = Persistence.getPersistenceUtil();

		//Using a custom FetchGroup now causes the isLoaded(entity) method to return "false"! 
		assertTrue("RootEntity, with the custom FetchGroup, should NOT be found fully loaded", !pUtil.isLoaded(ent));

		//Even using it on the actual loaded attribute is treated as NOT LOADED!
		assertAttNotLoaded_PersUtil(ent, "data1");

		assertAttNotLoaded_PersUtil(ent, "data2");

		//the associations should still return false
		assertAttNotLoaded_PersUtil(ent, "ownedAssoc");
		assertAttNotLoaded_PersUtil(ent, "owningAssoc");
		assertAttNotLoaded_PersUtil(ent, "keyCollectionAssoc");
		assertAttNotLoaded_PersUtil(ent, "refCollectionAssoc");

		//as a bonus, let's throw in some nonexistent attribute
		assertAttNotLoaded_PersUtil(ent, "some_nonexistent_attribute");

		em.close();
	}

	private static FetchGroup applyFetchGroupToQuery(Query query, String... atts) {
		FetchGroup fg = new FetchGroup();
		for (String oneAtt : atts) {
			fg.addAttribute(oneAtt);
		}
		query.setHint(QueryHints.FETCH_GROUP, fg);
		return fg;
	}

	/**
	 * 
	 * What just happened?
	 * 
	 * True enough, ProviderUtil holds true to its comments that it checks against DEFAULT/INITIAL SETTINGS, otherwise
	 * it "breaks completely" since it is unusable otherwise, other than telling if an entity uses a dynamically customized fetch group.
	 * 
	 * Honestly, PersistenceUtil method names seemed to provide a utility to check entity state regardless of
	 * how it was fetched. It was quite misleading.
	 * 
	 * Of course, this raises red flags in using the utility for entities that were fetched using a dynamic custom FetchGroup as
	 * dynamic configuration using custom FetchGroups is pretty much normal usage.
	 * 
	 * Moreover, with the bonus at last assert, we find that a result of "false" was produced. Either
	 * it failed silently (which could allow some programming errors) or it was completely ignored (the answer is the latter).
	 * 
	 * 
	 * 
	 * To quickly describe Eclipselink implementation (and to save some confusion), I'm going to mention it right now that for 
	 * whenever a method of PersistenceUtil, the Eclipselink implementation first checks whether the entire entity is "loaded". 
	 * If it returns "true", only then will the specific attribute be evaluated, else "false" is returned altogether.
	 * 
	 * Eclipselink decides whether an entity is loaded or not by iterating through all its attributes and associations until it finds one
	 * that is declared to be LAZY by default (via ORM XML or annotations), but is not loaded (via custom FetchGroup, among other ways of dynamic
	 * configuration), causing a return value of "false" ("true" if no such attribute or association was found).
	 * 
	 * In short, if the custom FetchGroup is not aligned with the default configuration, all of the PersistenceUtil methods will
	 * return "false".
	 * 
	 * 
	 */

	/**
	 * 
	 * To further prove this point, let's add an association to the custom FetchGroup.
	 */

	@Test
	public void CUSTOM_FetchGroup_WITH_BASIC_AND_ASSOC() {
		EntityManager em = createEM();
		TypedQuery<RootEntity> query = em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class);
		query.setParameter("id", 1L);

		//This time, the association "ownedAssoc" is included
		applyFetchGroupToQuery(query, "data1", "ownedAssoc");

		RootEntity ent = query.getSingleResult();
		PersistenceUtil pUtil = Persistence.getPersistenceUtil();

		//due to the custom fetch group, whole entity be found "not loaded"
		assertTrue("Due to custom FetchGroup, false should be returned", !pUtil.isLoaded(ent));

		//due to the custom fetch group, any attribute/association will be found "not loaded"
		assertAttNotLoaded_PersUtil(ent, "data1");
		assertAttNotLoaded_PersUtil(ent, "data2");
		assertAttNotLoaded_PersUtil(ent, "ownedAssoc");
		assertAttNotLoaded_PersUtil(ent, "owningAssoc");
		assertAttNotLoaded_PersUtil(ent, "keyCollectionAssoc");
		assertAttNotLoaded_PersUtil(ent, "refCollectionAssoc");

		em.close();
	}

	/**
	 * 
	 * Also to further prove the point, let's try a custom FetchGroup that is aligned with the defaults.
	 */

	@Test
	public void CUSTOM_FetchGroup_ALIGNED_WITH_DEFAULT() {
		EntityManager em = createEM();
		TypedQuery<RootEntity> query = em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class);
		query.setParameter("id", 1L);

		//This time, the association "ownedAssoc" is included
		applyFetchGroupToQuery(query, "data1", "data2");

		RootEntity ent = query.getSingleResult();
		PersistenceUtil pUtil = Persistence.getPersistenceUtil();

		//due to the custom FetchGroup being aligned with the default, the util should work as expected again
		assertTrue("Due to custom aligned FetchGroup, util should work as expected", pUtil.isLoaded(ent));
		assertAttLoaded_PersUtil(ent, "data1");
		assertAttLoaded_PersUtil(ent, "data2");

		assertAttNotLoaded_PersUtil(ent, "ownedAssoc");
		assertAttNotLoaded_PersUtil(ent, "owningAssoc");
		assertAttNotLoaded_PersUtil(ent, "keyCollectionAssoc");
		assertAttNotLoaded_PersUtil(ent, "refCollectionAssoc");

		em.close();
	}

	/**
	 * 
	 * At this point, it would be of great help to read and trace the code that handles this.
	 * 
	 * As a quick guide, it begins with javax.persistence.Persistence which returns a javax.persistence.Persistence.PersistenceUtil,
	 * which relies on a javax.persistence.spi.ProviderUtil implementation.
	 * 
	 * Eclipselink implements ProviderUtil in org.eclipse.persistence.jpa.PersistenceProvider. Its overridden ProviderUtil methods rely on
	 * org.eclipse.persistence.internal.jpa.EntityManagerFactoryImpl.isLoaded, which makes use of other internals - mainly
	 * org.eclipse.persistence.descriptors.ClassDescriptor and org.eclipse.persistence.queries.FetchGroupTracker.
	 * 
	 * NOTE: this is based on Eclipselink 2.5.2, and may change.
	 * 
	 */

	/**
	 * 
	 * Now how are we supposed to accurately identify fetch state if PersistenceUtil is unusable for
	 * entities fetched with dynamic fetch customizations?
	 * 
	 * There are some ways to identify fetch state using the Eclipselink API.
	 * 
	 * Before anything, it would be worth checking if the entity is woven - after all, it is only woven entities
	 * that can have uninitialized attributes. This can be done by instanceof checking for 
	 * org.eclipse.persistence.internal.weaving.PersistenceWeaved (or org.eclipse.persistence.queries.FetchGroupTracker
	 * since we are specifically dealing with FetchGroup management).
	 * 
	 * FetchGroupTracker is an interface added to entities during weaving, and it has methods to check for fetch state:
	 * 	-FetchGroupTracker._persistence_isAttributeFetched(String attributeName)
	 * 	-FetchGroupTracker._persistence_getFetchGroup()
	 * 
	 * NOTE: its implications in working with other weaving capabilities are not explored in this endeavor; better to use the default
	 * set of weaving capabilities.
	 * 
	 * Simply cast the entity to FetchGroupTracker and use its methods.
	 * 
	 * It seems deep and provider-specific enough to work as want it, but let me prove otherwise with the following test.
	 * 
	 */

	@Test
	public void FETCH_GROUP_TRACKER_always_returns_true_when_no_FetchGroup() {
		EntityManager em = createEM();
		RootEntity ent = findRootEntityById(1L, em);

		assertTrue("To enable lazy initialization, an entity should be woven for fetch group tracking",
				ent instanceof FetchGroupTracker);

		//Like mentioned in the beginning entities without an EXPLICIT LAZY CONFIGURATION ON A BASIC ATTRIBUTE will 
		//NOT HAVE A DEFAULT FETCH GROUP

		FetchGroupTracker fgt = (FetchGroupTracker) ent;
		assertTrue("An entity without a default LAZY basic attribute should not have a default FetchGroup",
				fgt._persistence_getFetchGroup() == null);

		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("data1"));
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("data2"));

		//even if the checked attributes are not initialized, TRUE is returned
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("ownedAssoc"));
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("owningAssoc"));
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("keyCollectionAssoc"));
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("refCollectionAssoc"));

		//even if the attribute does not exist in the first place
		assertTrue("Without a FetchGroup, a tracker would always return true",
				fgt._persistence_isAttributeFetched("nonexistent_att"));

		em.close();
	}

	/**
	 * 
	 * This is actually because of the implementation of the woven entities, where _persistence_isAttributeFetched
	 * would contain the following implementation when decompiled (again, as of 2.5.2, which may change in the future):
	 * 
	 * public boolean _persistence_isAttributeFetched(String paramString)
	 * {
	 *	   return (this._persistence_fetchGroup == null) || (this._persistence_fetchGroup.containsAttributeInternal(paramString));
	 * }
	 * 
	 * Observe the first condition in the disjunction which renders the method ineffective if there is no FetchGroup involved.
	 * 
	 * 
	 */

	/**
	 * 
	 * Now what?
	 * 
	 * If we observe more of the decompiled code of woven entities, we will find that org.eclipse.persistence.indirection.ValueHolder
	 * interface (can be WeavedAttributeValueHolderInterface or whatever) counterparts of the association members were also
	 * introduced in the code.
	 * 
	 * These value holders are responsible for indirection between associations, which allows their lazy loading. This is a clue
	 * as to the next ways to identify fetch state.
	 * 
	 * If we recall ProviderUtils, the Eclipselink implementation would use overloads of 
	 * org.eclipse.persistence.jpa.EntityManagerFactoryImpl.isLoaded().
	 * 
	 * More specifically, the following methods:
	 * 
	 * 	-static Boolean isLoaded(Object entity, String attributeName, AbstractSession session)
	 * 		-this method has the session as an extra requirement; it can be obtained via getServerSession() from EntityManagerFactoryImpl
	 * 		-its implementation makes sense if you read it, and should produce the desired results
	 * 		-it delegates to another overloaded method (the next method described)
	 * 		-it can return NULL when passed a nonexistent attribute
	 * 
	 *  -static boolean isLoaded(Object entity, String attributeName, DatabaseMapping mapping)
	 *  	-more specific as it requires a DatabaseMapping instead; it is obtained from a ClassDescriptor from the Session
	 *  		-see implementation of previous method for details
	 *  	-differentiates between associations (ForeignReferenceMappings) and basic attributes; handles each type correctly
	 *  	-will NOT return NULL
	 *  
	 *  -boolean isLoaded(Object entity, String attributeName)
	 *  	-non-static
	 *  	-ends up using the first method described (which takes an AbstractSession)
	 * 
	 * NOTE: there are two more isLoaded methods; they deal with checking the whole entity rather than specific attributes. These
	 * are actually the ones responsible for taking over the implementation of PersistenceUtil, so they won't be used.
	 * 
	 * This seems about right. Let's try it now.
	 * 
	 * NOTE: be careful where the AbstractSession is obtained from; if obtained from woven entities, NULL may be returned as it is
	 * with the "transient" nonaccess modifier!
	 */

	@Test
	public void CHECKING_ENTITY_WITH_NO_DEFAULT_FETCHGROUP_works_properly() {
		EntityManager em = createEM();
		RootEntity ent = findRootEntityById(1L, em);

		assertAttLoaded_EMF(ent, "data1");
		assertAttLoaded_EMF(ent, "data2");
		assertAttNotLoaded_EMF(ent, "ownedAssoc");
		assertAttNotLoaded_EMF(ent, "owningAssoc");
		assertAttNotLoaded_EMF(ent, "keyCollectionAssoc");
		assertAttNotLoaded_EMF(ent, "refCollectionAssoc");
	}

	private static void assertAttLoaded_EMF(Object entity, String attName) {
		EntityManagerFactoryImpl emfi = (EntityManagerFactoryImpl) emf;
		AbstractSession sesh = emfi.getServerSession();

		String msg = "Attribute/Association " + attName + " of [" + entity.getClass().getSimpleName()
				+ "] should be loaded.";

		//using first overridden method
		assertTrue(msg, EntityManagerFactoryImpl.isLoaded(entity, attName, sesh));
		//using third overridden method; the second one was skipped since the first one uses it too (though the third uses the first, lol);
		assertTrue(msg, emfi.isLoaded(entity, attName));
	}

	private static void assertAttNotLoaded_EMF(Object entity, String attName) {
		EntityManagerFactoryImpl emfi = (EntityManagerFactoryImpl) emf;
		AbstractSession sesh = emfi.getServerSession();

		String msg = "Attribute/Association " + attName + " of [" + entity.getClass().getSimpleName()
				+ "] should NOT be loaded.";
		//using first overridden method
		assertTrue(msg, !EntityManagerFactoryImpl.isLoaded(entity, attName, sesh));
		//using third overridden method; the second one was skipped since the first one uses it too (though the third uses the first, lol);
		assertTrue(msg, !emfi.isLoaded(entity, attName));
	}

	@Test
	public void CHECKING_ENTITY_DEFAULT_FETCHGROUP_works_properly() {
		EntityManager em = createEM();
		OwnedAssoc ownedAssoc = findOwnedAssocById(1L, em);

		assertAttNotLoaded_EMF(ownedAssoc, "data1");
		assertAttLoaded_EMF(ownedAssoc, "data2");
		em.close();
	}

	@Test
	public void CHECKING_ENTITY_CUSTOM_FETCHGROUP_works_properly() {
		EntityManager em = createEM();
		TypedQuery<RootEntity> query = em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class);
		query.setParameter("id", 1L);

		//Let's make this quite a big one
		applyFetchGroupToQuery(query, "data1", "ownedAssoc.data1", "owningAssoc.data2", "keyCollectionAssoc.data1",
				"refCollectionAssoc.data2");
		RootEntity ent = query.getSingleResult();

		//ROOT ENTITY
		assertAttLoaded_EMF(ent, "data1");
		assertAttNotLoaded_EMF(ent, "data2");
		//fetch groups don't make associations eager, so they should return false for now
		assertAttNotLoaded_EMF(ent, "ownedAssoc");
		assertAttNotLoaded_EMF(ent, "owningAssoc");
		assertAttNotLoaded_EMF(ent, "keyCollectionAssoc");
		assertAttNotLoaded_EMF(ent, "refCollectionAssoc");

		//the associations will now be initialized, but they will use the defined custom fetch group

		//OWNED ASSOC
		OwnedAssoc ownedAssoc = ent.getOwnedAssoc();
		assertAttLoaded_EMF(ownedAssoc, "data1");
		assertAttNotLoaded_EMF(ownedAssoc, "data2");

		//OWNING ASSOC
		OwningAssoc owningAssoc = ent.getOwningAssoc();
		assertAttNotLoaded_EMF(owningAssoc, "data1");
		assertAttLoaded_EMF(owningAssoc, "data2");
		assertAttNotLoaded_EMF(owningAssoc, "ownedParent");

		//KEY MAPPING COLLECTION ASSOC
		assertTrue("There should be two KeyMappingCollectionAssoc entries", ent.getKeyCollectionAssoc().size() == 2);
		for (KeyMappingCollectionAssoc oneAssoc : ent.getKeyCollectionAssoc()) {
			assertAttLoaded_EMF(oneAssoc, "data1");
			assertAttNotLoaded_EMF(oneAssoc, "data2");
			assertAttNotLoaded_EMF(oneAssoc, "parentId");
		}

		//REF MAPPING COLLECTION ASSOC
		assertTrue("There should be two RefyMappingCollectionAssoc entries", ent.getRefCollectionAssoc().size() == 2);
		for (ReferenceMappingCollectionAssoc oneAssoc : ent.getRefCollectionAssoc()) {
			assertAttNotLoaded_EMF(oneAssoc, "data1");
			assertAttLoaded_EMF(oneAssoc, "data2");
			assertAttNotLoaded_EMF(oneAssoc, "parentId");
		}

		em.close();
	}

	/**
	 * 
	 * Before we wrap up, let's see what happens if we use these techniques on a completely new entity.
	 * 
	 */

	@Test
	public void NEW_ENTITIES_are_completely_loaded() {
		RootEntity ent = new RootEntity();
		FetchGroupTracker fgtEnt = (FetchGroupTracker) ent;
		assertTrue("New entities have no FetchGroup", fgtEnt._persistence_getFetchGroup() == null);

		//using FetchGroupTracker
		//it's pretty much unnecessary to test all attributes in this case as a NULL fetch group would always cause
		//a return value of TRUE
		assertTrue(fgtEnt._persistence_isAttributeFetched("data1"));
		assertTrue(fgtEnt._persistence_isAttributeFetched("data2"));
		assertTrue(fgtEnt._persistence_isAttributeFetched("ownedAssoc"));
		assertTrue(fgtEnt._persistence_isAttributeFetched("owningAssoc"));
		assertTrue(fgtEnt._persistence_isAttributeFetched("keyCollectionAssoc"));
		assertTrue(fgtEnt._persistence_isAttributeFetched("refCollectionAssoc"));

		//using PersistenceUtil
		assertTrue(Persistence.getPersistenceUtil().isLoaded(ent));
		assertAttLoaded_PersUtil(ent, "data1");
		assertAttLoaded_PersUtil(ent, "data2");
		assertAttLoaded_PersUtil(ent, "ownedAssoc");
		assertAttLoaded_PersUtil(ent, "owningAssoc");
		assertAttLoaded_PersUtil(ent, "keyCollectionAssoc");
		assertAttLoaded_PersUtil(ent, "refCollectionAssoc");

		//using EntityManagerFactoryImpl
		assertAttLoaded_EMF(ent, "data1");
		assertAttLoaded_EMF(ent, "data2");
		assertAttLoaded_EMF(ent, "ownedAssoc");
		assertAttLoaded_EMF(ent, "owningAssoc");
		assertAttLoaded_EMF(ent, "keyCollectionAssoc");
		assertAttLoaded_EMF(ent, "refCollectionAssoc");
	}

	/**
	 * 
	 * Looks like that, even if only for new entities, all of the techniques discussed here agree that
	 * the attributes are defined as loaded.
	 * 
	 */

	/**
	 * 
	 * To wrap this up, a summary will be shown in a blog post (http://briaguy.blogspot.com/), where this
	 * code would be linked to anyway.
	 * 
	 * As a quick takeaway, the definite winner in this discussion is EntityManagerFactoryImpl.
	 * 
	 * Hope this helped!
	 * 
	 */
}
