package com.test.eclipselink.jpa.fetch_state.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * Entity implementation class for Entity: OwningAssoc
 *
 */
@Entity
@Table(name = "OWNING_ASSOC")
public class OwningAssoc implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	@JoinColumn(name = "OWNED_PARENT")
	@OneToOne(fetch = FetchType.LAZY)
	private RootEntity ownedParent;

	public OwningAssoc() {
		super();
	}

	public OwningAssoc(Long id, String data1, String data2) {
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

	public RootEntity getOwnedParent() {
		return ownedParent;
	}

	public void setOwnedParent(RootEntity ownedParent) {
		this.ownedParent = ownedParent;
	}

}
