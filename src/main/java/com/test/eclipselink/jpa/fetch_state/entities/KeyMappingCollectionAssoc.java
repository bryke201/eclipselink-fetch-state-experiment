package com.test.eclipselink.jpa.fetch_state.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Entity implementation class for Entity: OwnedCollectionAssoc
 *
 */
@Entity
@Table(name = "KEY_COLLECTION_ASSOC")
public class KeyMappingCollectionAssoc implements Serializable {

	private static final long serialVersionUID = 1L;
	@Id
	private Long id;
	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	@Column(name = "PARENT_ID")
	private Long parentId;

	public KeyMappingCollectionAssoc() {
		super();
	}

	public KeyMappingCollectionAssoc(Long id, String data1, String data2) {
		super();
		this.id = id;
		this.data1 = data1;
		this.data2 = data2;
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

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

}
