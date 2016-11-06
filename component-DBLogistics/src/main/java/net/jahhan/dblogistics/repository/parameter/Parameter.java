package net.jahhan.dblogistics.repository.parameter;
/*
 * Copyright 2008-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.lang.String.format;

import java.util.Arrays;
import java.util.List;

import net.jahhan.constant.SystemErrorCode;
import net.jahhan.dblogistics.annotation.Param;
import net.jahhan.dblogistics.annotation.SubDoc;
import net.jahhan.dblogistics.domain.Pageable;
import net.jahhan.dblogistics.domain.Sort;
import net.jahhan.utils.Assert;

/**
 * Class to abstract a single parameter of a query method. It is held in the
 * context of a {@link Parameters} instance.
 * 
 * @author Oliver Gierke
 */
public class Parameter {

	static final List<Class<?>> TYPES = Arrays.asList(Pageable.class, Sort.class);

	private static final String NAMED_PARAMETER_TEMPLATE = ":%s";
	private static final String POSITION_PARAMETER_TEMPLATE = "?%s";

	private final MethodParameter parameter;

	/**
	 * Creates a new {@link Parameter} for the given {@link MethodParameter}.
	 * 
	 * @param parameter
	 *            must not be {@literal null}.
	 */
	protected Parameter(MethodParameter parameter) {

		Assert.notNull(parameter, SystemErrorCode.UNKOWN_ERROR);
		this.parameter = parameter;
	}

	/**
	 * Returns whether the parameter is a special parameter.
	 * 
	 * @return
	 * @see #TYPES
	 */
	public boolean isSpecialParameter() {
		return TYPES.contains(parameter.getParameterType());
	}

	/**
	 * Returns whether the {@link Parameter} is to be bound to a query.
	 * 
	 * @return
	 */
	public boolean isBindable() {
		return !isSpecialParameter();
	}

	/**
	 * Returns the placeholder to be used for the parameter. Can either be a
	 * named one or positional.
	 * 
	 * @return
	 */
	public String getPlaceholder() {

		if (isNamedParameter()) {
			return format(NAMED_PARAMETER_TEMPLATE, getName());
		} else {
			return format(POSITION_PARAMETER_TEMPLATE, getIndex());
		}
	}

	/**
	 * Returns the position index the parameter is bound to in the context of
	 * its surrounding {@link Parameters}.
	 * 
	 * @return
	 */
	public int getIndex() {
		return parameter.getParameterIndex();
	}

	/**
	 * Returns whether the parameter is annotated with {@link Param}.
	 * 
	 * @return
	 */
	public boolean isNamedParameter() {
		return !isSpecialParameter() && getName() != null;
	}

	/**
	 * Returns the name of the parameter (through {@link Param} annotation) or
	 * null if none can be found.
	 * 
	 * @return
	 */
	public String getName() {

		Param annotation = parameter.getParameterAnnotation(Param.class);
		return annotation == null ? parameter.getParameterName() : annotation.value();
	}

	public boolean isSubDoc() {
		SubDoc annotation = parameter.getParameterAnnotation(SubDoc.class);
		return annotation == null ? false : true;
	}

	/**
	 * Returns the type of the {@link Parameter}.
	 * 
	 * @return the type
	 */
	public Class<?> getType() {
		return parameter.getParameterType();
	}

	/**
	 * Returns whether the parameter is named explicitly, i.e. annotated with
	 * {@link Param}.
	 * 
	 * @return
	 * @since 1.11
	 */
	public boolean isExplicitlyNamed() {
		return parameter.hasParameterAnnotation(Param.class);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return format("%s:%s", isNamedParameter() ? getName() : "#" + getIndex(), getType().getName());
	}

	/**
	 * Returns whether the {@link Parameter} is a {@link Pageable} parameter.
	 * 
	 * @return
	 */
	boolean isPageable() {
		return Pageable.class.isAssignableFrom(getType());
	}

	/**
	 * Returns whether the {@link Parameter} is a {@link Sort} parameter.
	 * 
	 * @return
	 */
	boolean isSort() {
		return Sort.class.isAssignableFrom(getType());
	}
}