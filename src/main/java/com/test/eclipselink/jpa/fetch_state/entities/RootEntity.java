package com.test.eclipselink.jpa.fetch_state.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.eclipse.persistence.annotations.PrivateOwned;

/**
 * Entity implementation class for Entity: RootEntity
 *
 */
@Entity
@Table(name = "ROOT_ENTITY")
public class RootEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	@JoinColumn(name = "OWNED_FK")
	@OneToOne(fetch = FetchType.LAZY)
	private OwnedAssoc ownedAssoc;

	@OneToOne(mappedBy = "ownedParent", fetch = FetchType.LAZY)
	private OwningAssoc owningAssoc;

	@PrivateOwned
	@JoinColumn(name = "PARENT_ID", referencedColumnName = "ID")
	@OneToMany(cascade = CascadeType.PERSIST)
	private List<KeyMappingCollectionAssoc> keyCollectionAssoc = new ArrayList<>();

	@PrivateOwned
	@OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST)
	private List<ReferenceMappingCollectionAssoc> refCollectionAssoc = new ArrayList<>();

	public RootEntity() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getData1() {
		return data1;
	}

	public void setData1(String data1) {
		this.data1 = data1;
	}

	public String getData2() {
		return data2;
	}

	public void setData2(String data2) {
		this.data2 = data2;
	}

	public OwnedAssoc getOwnedAssoc() {
		return ownedAssoc;
	}

	public void setOwnedAssoc(OwnedAssoc ownedAssoc) {
		this.ownedAssoc = ownedAssoc;
	}

	public OwningAssoc getOwningAssoc() {
		return owningAssoc;
	}

	public void setOwningAssoc(OwningAssoc owningAssoc) {
		this.owningAssoc = owningAssoc;
	}

	public List<KeyMappingCollectionAssoc> getKeyCollectionAssoc() {
		return keyCollectionAssoc;
	}

	public void addKeyCollectionAssoc(KeyMappingCollectionAssoc assoc) {
		keyCollectionAssoc.add(assoc);
	}

	public List<ReferenceMappingCollectionAssoc> getRefCollectionAssoc() {
		return refCollectionAssoc;
	}

	public void addRefCollectionAssoc(ReferenceMappingCollectionAssoc assoc) {
		assoc.setParent(this);
		refCollectionAssoc.add(assoc);
	}

}
