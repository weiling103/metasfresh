package de.metas.fresh.mrp_productinfo.model.validator;

/*
 * #%L
 * de.metas.edi
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.compiere.model.I_M_Transaction;
import org.compiere.model.ModelValidator;

import de.metas.fresh.mrp_productinfo.async.spi.impl.UpdateMRPProductInfoTableWorkPackageProcessor;

@Interceptor(I_M_Transaction.class)
public class M_Transaction
{
	public static final M_Transaction INSTANCE = new M_Transaction();

	private M_Transaction()
	{
	}

	@ModelChange(timings = { ModelValidator.TYPE_BEFORE_NEW,
			ModelValidator.TYPE_BEFORE_CHANGE,
			ModelValidator.TYPE_BEFORE_DELETE })
	public void enqueueMaterialTransaction(final I_M_Transaction transaction)
	{
		UpdateMRPProductInfoTableWorkPackageProcessor.schedule(transaction);
	}
}
