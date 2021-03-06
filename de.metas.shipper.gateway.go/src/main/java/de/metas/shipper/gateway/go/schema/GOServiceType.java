package de.metas.shipper.gateway.go.schema;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;

import de.metas.shipper.gateway.spi.model.ServiceType;
import de.metas.util.GuavaCollectors;
import lombok.Getter;
import lombok.NonNull;

/*
 * #%L
 * de.metas.shipper.go
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public enum GOServiceType implements ServiceType
{
	Overnight("0"), OvernightLetter("1"), International("2"), InternationalLetter("3");

	@Getter
	private final String code;

	private GOServiceType(final String code)
	{
		this.code = code;
	}

	public static GOServiceType forCode(@NonNull final String code)
	{
		final GOServiceType type = code2type.get(code);
		if (type == null)
		{
			throw new NoSuchElementException("No element found for code=" + code);
		}
		return type;
	}

	private static final ImmutableMap<String, GOServiceType> code2type = Stream.of(values())
			.collect(GuavaCollectors.toImmutableMapByKey(GOServiceType::getCode));
}
