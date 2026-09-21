/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.dashery.flippingtables;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("flippingtables")
public interface FlippingTablesConfig extends Config
{
	@ConfigItem(
		position = 1,
		keyName = "apiBaseUrl",
		name = "API address",
		description = "HTTPS service receiving the portfolio when you request advice"
	)
	default String apiBaseUrl()
	{
		return "https://flippingtables.91-98-161-245.sslip.io/api";
	}

	@ConfigItem(
			position = 2,
			keyName = "nextVisitHours",
			name = "Hours until next visit",
			description = "Default time until your next Grand Exchange visit, from 1 to 168 hours"
	)
	default int nextVisitHours()
	{
		return 4;
	}

	@ConfigItem(
			position = 3,
			keyName = "followingVisitHours",
			name = "Hours until following visit",
			description = "Default interval between the next visit and the following visit; defaults to the next-visit value"
	)
	default int followingVisitHours()
	{
		return nextVisitHours();
	}

	@ConfigItem(
			position = 4,
			keyName = "recordOffers",
			name = "Record GE offers",
			description = "Record Grand Exchange offer counters and sync them to the configured Flipping Tables API"
	)
	default boolean recordOffers()
	{
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = "volumeParticipationPercent",
			name = "Volume participation (%)",
			description = "Conservative share of observed trade volume used for estimates, from 1 to 100"
	)
	default int volumeParticipationPercent()
	{
		return 10;
	}

}
