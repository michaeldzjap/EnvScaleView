EnvScaleView {
	var <view,<background,<font,<drawFunc,<horzGridDist,<maxHorzGridDist,<minHorzGridDist,<numVertGridLines,<gridColor,<gridWidth,<showHorzAxis,<showVertAxis,<breakPointSize,<curvePointRadius,<domainMode,<minRange,<maxRange,<unitMode,<scaleResponsiveness,<env,envData,envView,rangeView,settingsView,timeStep,numGridLinesPerUnit,vhorzGridDist,selGridLineCoord,loopStartNode,loopEndNode;
	classvar unitStep;

	*initClass {
		Class.initClassTree(Array);
		Class.initClassTree(IdentityDictionary);
		unitStep = [
			IdentityDictionary.new.add(\time -> 100.0).add(\tempo -> [50,1]),
			IdentityDictionary.new.add(\time -> 50.0).add(\tempo -> [25,1]),
			IdentityDictionary.new.add(\time -> 20.0).add(\tempo -> [10,1]),
			IdentityDictionary.new.add(\time -> 10.0).add(\tempo -> [5,1]),
			IdentityDictionary.new.add(\time -> 5.0).add(\tempo -> [5,2]),
			IdentityDictionary.new.add(\time -> 2.0).add(\tempo -> [1,1]),
			IdentityDictionary.new.add(\time -> 1.0).add(\tempo -> [1,2]),
			IdentityDictionary.new.add(\time -> 0.5).add(\tempo -> [1,4]),
			IdentityDictionary.new.add(\time -> 0.2).add(\tempo -> [1,8]),
			IdentityDictionary.new.add(\time -> 0.1).add(\tempo -> [1,16]),
			IdentityDictionary.new.add(\time -> 0.05).add(\tempo -> [1,32]),
			IdentityDictionary.new.add(\time -> 0.02).add(\tempo -> [1,64]),
			IdentityDictionary.new.add(\time -> 0.01).add(\tempo -> [1,128]),
			IdentityDictionary.new.add(\time -> 0.005).add(\tempo -> [1,256])
		]
	}

	*new { arg parent,bounds,env;
		bounds = bounds ? (parent.notNil.if { parent.view.bounds } { nil } ? Rect(100,300,600,300));
		env = env ? Env([0,1,1,0],[0,0.5,0],[0,0,0],nil,nil);
		env.curves.respondsTo(\at).not.if { env.curves = { env.curves } ! env.times.size };
		^super.new.init(parent,bounds,env)
	}

	init { arg parent,bounds,env;
		var prevPoint,brPtModeMenu,breakPointMode,ctlKeyDown = false,quantCoord,remNumGridLines = 0,scaleCount = 0,scaleRespCount = 0,onBreakPoint,onCurvePoint,dbreakPointXCoords,initBreakPointTime,dinitBreakPointTime;

		numGridLinesPerUnit = 8;
		timeStep = 0.2;

		loopStartNode = 1;
		loopEndNode = 2;

		// initialize grid vars
		this.gridWidth_(0.125,false);
		this.gridColor_(Color.grey,false);
		this.numVertGridLines_(20,false);
		this.horzGridDist_(20,false);
		this.maxHorzGridDist_(horzGridDist*2,false);
		this.minHorzGridDist_(horzGridDist*0.5,false);
		selGridLineCoord = ((bounds.width/horzGridDist).div(2)*horzGridDist)@(bounds.width/horzGridDist/numGridLinesPerUnit*0.5*timeStep);
		quantCoord = selGridLineCoord.copy;
		vhorzGridDist = horzGridDist;

		// initialize axes vars
		this.showHorzAxis_(true,false);
		this.showVertAxis_(true,false);

		// initialize break point and curve point vars
		this.breakPointSize_(7,false);
		this.curvePointRadius_(2.5,false);

		// initialize other vars
		this.font_(Font("Courier",12),false);
		this.unitMode_(\time,false);
		this.domainMode_(\unipolar,false);
		this.minRange_(0,false);
		this.maxRange_(1,false);
		this.scaleResponsiveness_(3);

		view = View(parent,bounds).resize_(5).background_(Color.white);

		settingsView = View().layout_(
			HLayout(
				[
					NumberBox().value_(0).minWidth_(42).maxWidth_(46).align_(\right).action_({ arg number;
						(number.value > maxRange).if {
							minRange = maxRange;
							maxRange = number.value
						};
						minRange = number.value;
						rangeView.refresh
					}),s:1
				],
				StaticText().string_("minimum").font_(font),
				[
					NumberBox().value_(1).minWidth_(42).maxWidth_(46).align_(\right).action_({ arg number;
						(number.value < minRange).if {
							maxRange = minRange;
							minRange = number.value
						};
						maxRange = number.value;
						rangeView.refresh
					}),s:1
				],
				StaticText().string_("maximum").font_(font),
				nil,
				PopUpMenu().items_(["time","tempo"]).font_(font).action_({ arg menu;
					unitMode = menu.item.asSymbol;
					//numGridLinesPerUnit = (unitMode == \time).if { 5 } { 8 }
				}).valueAction_(0),
				brPtModeMenu = PopUpMenu().items_(["chained","loose"]).font_(font).action_({ arg menu;
					breakPointMode = menu.item.asSymbol
				}).valueAction_(0)
			).margins_(0)
		);

		rangeView = UserView(parent,Rect(bounds.left,bounds.top,bounds.width*0.05,bounds.height)).background_(parent.notNil.if { parent.background } { nil }).resize_(5);

		envView = UserView(parent,rangeView.bounds.right,bounds.top,bounds.width - rangeView.bounds.width,bounds.height).background_(Color(218/255,232/255,238/255,1)).resize_(5).drawFunc_({ |me|
			var pos,vertGridDist,timeIncr,rem,lineResolution = 50;

			// draw horizontal lines (fixed)
			Pen.width = gridWidth;
			Pen.strokeColor = gridColor;
			vertGridDist = me.bounds.height/numVertGridLines;
			(me.bounds.height/vertGridDist).ceil do: { |i|
				var y = i*vertGridDist;
				Pen.moveTo(0@y);
				Pen.lineTo(me.bounds.width@y)
			};
			Pen.stroke;

			Pen.font_(Font(font.name,font.size - 2));
			Pen.fillColor = gridColor;
			timeIncr = timeStep/numGridLinesPerUnit;
			Pen.strokeColor = gridColor;
			pos = selGridLineCoord.copy;
			rem = remNumGridLines;

			// draw vertical lines and units (avoid drawing lines and units outside bounds of the view)
			(selGridLineCoord.x >= 0 and: { selGridLineCoord.x <= me.bounds.width }).if {
				// selGridLineCoord.x is in the visible part of the view
				while({ pos.x <= me.bounds.width }) {
					Pen.moveTo(pos.x@0);
					Pen.lineTo(pos.x@me.bounds.height);
					(rem == 0).if {
						Pen.stringAtPoint(this.prMakeStr(pos.y.round(timeStep)),pos.x@(me.bounds.height/2 - 5))
					};
					pos.x = pos.x + vhorzGridDist;
					pos.y = pos.y + timeIncr;
					rem = rem + 1;
					(rem == numGridLinesPerUnit).if { rem = 0 }
				};
				pos.x = selGridLineCoord.x - vhorzGridDist;
				pos.y = selGridLineCoord.y - timeIncr;
				rem = remNumGridLines - 1;
				while({ pos.x >= vhorzGridDist.neg }) {
					(rem < 0).if { rem = numGridLinesPerUnit - 1 };
					Pen.moveTo(pos.x@0);
					Pen.lineTo(pos.x@me.bounds.height);
					(rem == 0).if {
						Pen.stringAtPoint(this.prMakeStr(pos.y.round(timeStep)),pos.x@(me.bounds.height/2 - 5))
					};
					pos.x = pos.x - vhorzGridDist;
					pos.y = pos.y - timeIncr;
					rem = rem - 1
				};
				/*Pen.stroke;

				Pen.strokeColor = Color.red;
				Pen.moveTo(selGridLineCoord.x@0);
				Pen.lineTo(selGridLineCoord.x@me.bounds.height)*/
			} {
				(selGridLineCoord.x < 0).if {
					// selGridLineCoord.x is to the left of the visible part of the view
					while({ pos.x < vhorzGridDist.neg }) {
						pos.x = pos.x + vhorzGridDist;
						pos.y = pos.y + timeIncr;
						rem = rem + 1;
						(rem == numGridLinesPerUnit).if { rem = 0 }
					};
					while({ pos.x <= me.bounds.width }) {
						Pen.moveTo(pos.x@0);
						Pen.lineTo(pos.x@me.bounds.height);
						(rem == 0).if {
							Pen.stringAtPoint(this.prMakeStr(pos.y.round(timeStep)),pos.x@(me.bounds.height/2 - 5))
						};
						pos.x = pos.x + vhorzGridDist;
						pos.y = pos.y + timeIncr;
						rem = rem + 1;
						(rem == numGridLinesPerUnit).if { rem = 0 }
					}
				} {
					// selGridLineCoord.x is to the right of the visible part of the view
					(rem == 0).if { rem = numGridLinesPerUnit };
					while({ pos.x > me.bounds.width }) {
						pos.x = pos.x - vhorzGridDist;
						pos.y = pos.y - timeIncr;
						rem = rem - 1;
						(rem == 0).if { rem = numGridLinesPerUnit };
					};
					while({ pos.x >= 0 }) {
						Pen.moveTo(pos.x@0);
						Pen.lineTo(pos.x@me.bounds.height);
						(rem == numGridLinesPerUnit).if {
							Pen.stringAtPoint(this.prMakeStr(pos.y.round(timeStep)),pos.x@(me.bounds.height/2 - 5))
						};
						pos.x = pos.x - vhorzGridDist;
						pos.y = pos.y - timeIncr;
						rem = rem - 1;
						(rem == 0).if { rem = numGridLinesPerUnit }
					}
				}
			};
			Pen.stroke;

			// first draw line segments
			Pen.strokeColor = Color.grey(0.5);
			Pen.fillColor = Color.grey(1,0.5);
			Pen.width = 1;
			Pen.moveTo(envData.breakPointCoords[0].x@(me.bounds.height + 1));
			envData.breakPointCoords do: { |breakPointCoord,i|
				(i != 0).if {

					lineResolution do: { |j|
						var k = j/(lineResolution - 1),xpos;

						xpos = envData.breakPointCoords[i - 1].x + (k*(breakPointCoord.x - envData.breakPointCoords[i - 1].x));

						xpos.inRange(vhorzGridDist.neg,me.bounds.width + vhorzGridDist).if {
							Pen.lineTo(
								xpos@(envData.breakPointCoords[i - 1].y + (env.curves[i - 1].asWarp.map(k)*(breakPointCoord.y - envData.breakPointCoords[i - 1].y)))
							)
						}

					}

				}
			};
			Pen.lineTo(envData.breakPointCoords.last.x@(me.bounds.height + 1));
			Pen.lineTo(0@(me.bounds.height + 1));
			Pen.fillStroke;

			// then draw breakpoints, curvepoints (if in visible part of the view)
			Pen.strokeColor = Color.red;
			envData.breakPointCoords do: { |breakPointCoord,i|

				(i == loopStartNode or: { i == loopEndNode }).if {

					Pen.width = 0.25;
					Pen.moveTo(breakPointCoord.x@0);
					Pen.lineTo(breakPointCoord.x@me.bounds.height);
					(i == loopEndNode).if {
						Pen.moveTo(breakPointCoord);
						Pen.lineTo((envData.breakPointCoords[loopStartNode].x - 2.5)@breakPointCoord.y)
					};
					Pen.stroke

				};

				Pen.width = 0.5;
				Pen.fillColor = (envData.selBreakPoint == i
					or: { envData.selBreakPoint == 0 and: { i == envData.breakPointCoords.lastIndex } }
					or: { envData.selBreakPoint == envData.breakPointCoords.lastIndex and: { i == 0 } }
				).if {
					Color.red
				} {
					Color.white
				};

				(breakPointCoord.x > vhorzGridDist.neg and: { breakPointCoord.x < (me.bounds.width + vhorzGridDist) }).if {
					Pen.addRect(Rect(breakPointCoord.x - (breakPointSize/2),breakPointCoord.y - (breakPointSize/2),breakPointSize,breakPointSize));
					(i > 0).if { Pen.addArc(envData.curvePointCoords[i - 1],curvePointRadius,0,2pi) }
				};

				(i > 0 and: { envData.curvePointCoords[i - 1].x.inRange(vhorzGridDist.neg,me.bounds.width + vhorzGridDist) }).if {
					Pen.addArc(envData.curvePointCoords[i - 1],curvePointRadius,0,2pi)
				};
				Pen.fillStroke

			}

		}).keyDownAction_({ |me,char,mod,key,uni|
			ctlKeyDown = mod.isCtrl
		}).keyUpAction_({ |me,char,mod,key,uni|
			ctlKeyDown = false
		}).mouseDownAction_({ |me,x,y,mod|
			var n = 0,int,unitDist = vhorzGridDist*numGridLinesPerUnit,timeIncr = timeStep/numGridLinesPerUnit,lim = breakPointSize/2;

			// determine vertical grid line which is closed to current cursor position
			(x > selGridLineCoord.x).if {
				while({ x - selGridLineCoord.x > vhorzGridDist.div(2) }) {
					selGridLineCoord.x = selGridLineCoord.x + vhorzGridDist;
					n = n + 1
				};
				selGridLineCoord.y = (selGridLineCoord.y + (n*timeIncr) + 1e-9).trunc(timeIncr);
				int = (selGridLineCoord.x - quantCoord.x).div(unitDist);
				quantCoord = (quantCoord.x + (int*unitDist))@(quantCoord.y + (int*timeStep) + 1e-9).trunc(timeStep)
			} {
				while({ selGridLineCoord.x - x > vhorzGridDist.div(2) }) {
					selGridLineCoord.x = selGridLineCoord.x - vhorzGridDist;
					n = n + 1
				};
				selGridLineCoord.y = (selGridLineCoord.y - (n*timeIncr) + 1e-9).trunc(timeIncr);
				int = (quantCoord.x - selGridLineCoord.x).div(unitDist);
				quantCoord = (quantCoord.x - (int*unitDist))@(quantCoord.y - (int*timeStep) + 1e-9).trunc(timeStep)
			};
			remNumGridLines = (selGridLineCoord.x - quantCoord.x).div(vhorzGridDist);
			(remNumGridLines < 0).if {
				remNumGridLines = numGridLinesPerUnit + remNumGridLines;
				quantCoord.x = quantCoord.x - unitDist;
				quantCoord.y = quantCoord.y - timeStep
			};

			onBreakPoint = false;
			envData.breakPointCoords do: { |breakPointCoord,i|
				((breakPointCoord.x - x).abs <= lim and: { (breakPointCoord.y - y).abs <= lim }).if {
					envData.selBreakPoint = i;
					onBreakPoint = true
				}
			};

			onCurvePoint = false;
			envData.curvePointCoords do: { |curvePointCoord,i|
				((curvePointCoord.x - x).abs <= lim and: { (curvePointCoord.y - y).abs <= lim }).if {
					envData.selBreakPoint = i + 1;
					onCurvePoint = true
				}
			};

			(breakPointMode == \chained and: { onBreakPoint } and: { envData.selBreakPoint > 0 }).if {
				dbreakPointXCoords = envData.breakPointCoords[envData.selBreakPoint..envData.breakPointCoords.lastIndex].performUnaryOp(\x).differentiate;
				dbreakPointXCoords.removeAt(0);
				initBreakPointTime = env.times[0..envData.selBreakPoint - 1].sum;
				dinitBreakPointTime = env.times[envData.selBreakPoint - 1]
			};

			ctlKeyDown.if {

				onBreakPoint.if {

					// delete break point except very first and don't delete any break points if there are only four left
					(envData.selBreakPoint != 0 and: { env.levels.size > 4 }).if { var tmp;

						// remove break point and associated curve point
						//env.levels.removeAt(envData.selBreakPoint);
						tmp = env.levels.copy;
						tmp.removeAt(envData.selBreakPoint);
						env.levels = tmp;
						tmp = env.times.removeAt(envData.selBreakPoint - 1);
						//env.times[envData.selBreakPoint - 1] = env.times[envData.selBreakPoint - 1] + tmp;
						env.setTime(envData.selBreakPoint - 1,env.times[envData.selBreakPoint - 1] + tmp);
						envData.breakPointCoords.removeAt(envData.selBreakPoint);
						envData.curvePointCoords.removeAt(envData.selBreakPoint - 1);
						//env.curves.removeAt(envData.selBreakPoint - 1);
						tmp = env.curves.copy;
						tmp.removeAt(envData.selBreakPoint - 1);
						env.curves = tmp;
						envData.calcXCurvePoint(envData.selBreakPoint - 1);
						envData.calcYCurvePoint(envData.selBreakPoint - 1);

						/*
						 * delete break point distance of previous selected breakpoint to new selected break point in chained mode
						 * and when not the last break point
						 */
						(breakPointMode == \chained and: { envData.selBreakPoint < envData.breakPointCoords.lastIndex }).if {
							dbreakPointXCoords.removeAt(0)
						}

					}

				} { var insertInd,breakPointTime;

					breakPointTime = (x == selGridLineCoord.x).if {
						selGridLineCoord.y
					} {
						(x - selGridLineCoord.x)*timeStep/(vhorzGridDist*numGridLinesPerUnit) + selGridLineCoord.y
					};

					//envData.breakPointCoords.postln;

					// insert break point and associated curve point and select it
					insertInd = envData.breakPointCoords.performUnaryOp(\x).indexOfGreaterThan(x) ? envData.breakPointCoords.size;
					env.levels = env.levels.insert(insertInd,y.linlin(0,me.bounds.height,maxRange,minRange));
					env.times = (insertInd == 1).if {
						env.times.insert(0,breakPointTime)
					} {
						env.times.insert(insertInd - 1,breakPointTime - env.times[0..insertInd - 2].sum)
					};
					(insertInd < env.times.lastIndex).if {
						//env.times[insertInd] = env.times[insertInd] - env.times[insertInd - 1]
						env.setTime(insertInd,env.times[insertInd] - env.times[insertInd - 1])
					};
					envData.breakPointCoords = envData.breakPointCoords.insert(insertInd,x@y);
					envData.curvePointCoords = envData.curvePointCoords.insert(insertInd - 1,(envData.breakPointCoords[insertInd] + envData.breakPointCoords[insertInd - 1])/2);
					env.curves = env.curves.insert(insertInd - 1,0);
					(insertInd < envData.breakPointCoords.lastIndex).if {
						envData.calcXCurvePoint(insertInd);
						envData.calcYCurvePoint(insertInd)
					};

					// if inserted break point is the last, update break and curve point data for first break point
					(insertInd == env.levels.lastIndex).if {
						//env.levels[0] = env.levels[insertInd];
						env.setLevel(0,env.levels[insertInd]);
						envData.breakPointCoords[0].y = envData.breakPointCoords[insertInd].y;
						envData.calcYCurvePoint(0)
					};

					// if in chained mode recalculate break point distance
					dbreakPointXCoords = envData.breakPointCoords[insertInd..envData.breakPointCoords.lastIndex].performUnaryOp(\x).differentiate;
					dbreakPointXCoords.removeAt(0);

					// shift loop nodes according to position of new break point
					(insertInd <= loopStartNode).if {
						loopStartNode = loopStartNode + 1
					};
					(insertInd <= loopEndNode).if {
						loopEndNode = loopEndNode + 1
					};

					envData.selBreakPoint = insertInd

				}

			};

			prevPoint = x@y;
			me.refresh
		}).mouseMoveAction_({ |me,x,y,mod|
			var dx = (x - prevPoint.x).clip(-10,10),dy = y - prevPoint.y,vleftNumGridLines,timeIncr,currZeroPos,breakPointLevel,breakPointTime,prevBreakPointTime,nextBreakPointTime;

			onBreakPoint.if {

				breakPointLevel = y.linlin(0,me.bounds.height,maxRange,minRange);

				(envData.selBreakPoint == 0).if {

					// first break point is only allowed to move up and down
					//env.levels[0] = breakPointLevel;
					env.setLevel(0,breakPointLevel);
					envData.breakPointCoords[0].y = y.clip(0,me.bounds.height);
					//env.levels[env.levels.lastIndex] = env.levels[0];
					env.setLevel(env.levels.lastIndex,env.levels[0]);
					envData.breakPointCoords[envData.breakPointCoords.lastIndex].y = envData.breakPointCoords[0].y;

					// adjust first and last curve points
					envData.calcYCurvePoint(0);
					envData.calcYCurvePoint(envData.curvePointCoords.lastIndex)

				} {

					breakPointTime = (x == selGridLineCoord.x).if {
						selGridLineCoord.y
					} {
						(x - selGridLineCoord.x)*timeStep/(vhorzGridDist*numGridLinesPerUnit) + selGridLineCoord.y
					};
					prevBreakPointTime = env.times[0..envData.selBreakPoint - 2].sum;

					/*
					 * if selected break point is the last, allow it to move up, down and to the right freely
					 * but not past preceding break point and also move first break point up and down
					 */
					(envData.selBreakPoint == env.levels.lastIndex).if {

						//env.levels[envData.selBreakPoint] = breakPointLevel;
						env.setLevel(envData.selBreakPoint,breakPointLevel);
						breakPointTime = breakPointTime.max(prevBreakPointTime);
						//env.times[envData.selBreakPoint - 1] = breakPointTime - prevBreakPointTime;
						env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
						envData.breakPointCoords[envData.selBreakPoint] = x.max(envData.breakPointCoords[envData.selBreakPoint - 1].x)@y.clip(0,me.bounds.height);
						//env.levels[0] = breakPointLevel;
						env.setLevel(0,breakPointLevel);
						envData.breakPointCoords[0].y = y.clip(0,me.bounds.height);

						// adjust first and last curve points
						envData.calcYCurvePoint(0);
						envData.calcXCurvePoint(envData.curvePointCoords.lastIndex);
						envData.calcYCurvePoint(envData.curvePointCoords.lastIndex)

					} {

						(envData.breakPointCoords[envData.selBreakPoint].x <= envData.breakPointCoords[envData.selBreakPoint - 1].x).if {

							/*
							 * if x-coordinate of selected break point is equal to the x-coordinate of the preceding break point,
							 * allow it to move up and down but not past preceding break point
							 */
							//env.levels[envData.selBreakPoint] = breakPointLevel;
							env.setLevel(envData.selBreakPoint,breakPointLevel);
							breakPointTime = breakPointTime.max(prevBreakPointTime);
							//env.times[envData.selBreakPoint - 1] = breakPointTime - prevBreakPointTime;
							env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
							envData.breakPointCoords[envData.selBreakPoint] = x.max(envData.breakPointCoords[envData.selBreakPoint - 1].x)@y.clip(0,me.bounds.height)

						} {

							/*
							 * if the x-coordinate of the break point is equal to the x-coordinate of the next break point,
							 * allow it to move up and down but not past the next break point when in loose mode
							 */
							(breakPointMode == \loose and: { envData.breakPointCoords[envData.selBreakPoint].x >= envData.breakPointCoords[envData.selBreakPoint + 1].x }).if {

								//env.levels[envData.selBreakPoint] = breakPointLevel;
								env.setLevel(envData.selBreakPoint,breakPointLevel);
								nextBreakPointTime = env.times[0..envData.selBreakPoint].sum;
								breakPointTime = breakPointTime.min(nextBreakPointTime);
								//env.times[envData.selBreakPoint - 1] = breakPointTime - prevBreakPointTime;
								env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
								envData.breakPointCoords[envData.selBreakPoint] = x.min(envData.breakPointCoords[envData.selBreakPoint + 1].x)@y.clip(0,me.bounds.height)

							} {

								// if none of the above conditions apply, we can move the break point to any position
								//env.levels[envData.selBreakPoint] = breakPointLevel;
								//env.times[envData.selBreakPoint - 1] = breakPointTime - prevBreakPointTime;
								env.setLevel(envData.selBreakPoint,breakPointLevel);
								env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
								envData.breakPointCoords[envData.selBreakPoint] = x@y.clip(0,me.bounds.height);

								// adjust curve points to the left and right of the break point
								envData.calcXCurvePoint(envData.selBreakPoint - 1);
								envData.calcYCurvePoint(envData.selBreakPoint - 1);
								envData.calcXCurvePoint(envData.selBreakPoint);
								envData.calcYCurvePoint(envData.selBreakPoint)

							};

							/*
							 * if in chained break point mode, shift all break points and curve points to the right of
							 * the selected break point an equal amount in the horizontal direction
							 */
							(breakPointMode == \chained).if {
								//env.times[envData.selBreakPoint - 1] = (dinitBreakPointTime + breakPointTime - initBreakPointTime).max(0);
								env.setTime(envData.selBreakPoint - 1,(dinitBreakPointTime + breakPointTime - initBreakPointTime).max(0));
								(envData.selBreakPoint + 1..envData.breakPointCoords.lastIndex) do: { |i,j|
									envData.breakPointCoords[i].x = envData.breakPointCoords[i - 1].x + dbreakPointXCoords[j];
									(j > 0).if {
										envData.curvePointCoords[i - 1].x = (envData.breakPointCoords[i - 1].x + envData.breakPointCoords[i].x)/2
									}
								}
							}

						}

					}

				}

			} {
				onCurvePoint.if {
					envData.calcYCurvePoint(envData.selBreakPoint - 1,dy)
				} {

					// if the mouse cursor is not on a selected break point or a curve point, scale or translate the view

					// translate view
					selGridLineCoord.x = selGridLineCoord.x + dx;
					quantCoord.x = quantCoord.x + dx;

					// zoom in / out at currently selected grid line
					(scaleRespCount == 0).if {

						vhorzGridDist = vhorzGridDist - dy;
						quantCoord.x = selGridLineCoord.x - (remNumGridLines*vhorzGridDist);
						(dy < 0).if {
							// mouse is dragged up
							(vhorzGridDist > maxHorzGridDist).if {

								scaleCount = scaleCount - 1;
								(scaleCount < 0).if { scaleCount = 2 };

								(scaleCount == 0).if {
									vhorzGridDist = minHorzGridDist;
									timeStep = (timeStep*0.4).round(1e-4);
									timeIncr = timeStep/numGridLinesPerUnit;
									(selGridLineCoord.y == selGridLineCoord.y.trunc(timeStep)).if {
										remNumGridLines = 0;
										quantCoord = selGridLineCoord.copy
									} {
										remNumGridLines = 2;
										selGridLineCoord = (selGridLineCoord.x - (0.5*vhorzGridDist))@(selGridLineCoord.y - (0.5*timeIncr)).round(1e-6);
										quantCoord = (selGridLineCoord.x - (vhorzGridDist*remNumGridLines ))@selGridLineCoord.y.trunc(timeStep)
									}
								} {
									vhorzGridDist = minHorzGridDist;
									timeStep = (timeStep*0.5).round(1e-4);
									timeIncr = timeStep/numGridLinesPerUnit;
									(remNumGridLines != 0).if {
										remNumGridLines = remNumGridLines*2;
										(remNumGridLines < numGridLinesPerUnit).if {
											quantCoord.x = selGridLineCoord.x - (remNumGridLines*vhorzGridDist)
										} {
											(remNumGridLines > numGridLinesPerUnit).if {
												remNumGridLines = remNumGridLines - numGridLinesPerUnit;
												quantCoord = (selGridLineCoord.x - (remNumGridLines*vhorzGridDist))@(quantCoord.y + timeStep)
											} {
												remNumGridLines = 0;
												quantCoord.x = selGridLineCoord.x
											}
										}
									}
								}

							}
						} {
							(dy > 0).if { var tmp1,oldTimeIncr;
								// mouse is dragged down
								(vhorzGridDist < minHorzGridDist).if {

									oldTimeIncr = timeStep/numGridLinesPerUnit;

									(scaleCount == 0).if {
										vhorzGridDist = maxHorzGridDist;
										timeStep = timeStep*2.5;
										tmp1 = selGridLineCoord.y.round(timeStep/numGridLinesPerUnit);
									} {
										vhorzGridDist = maxHorzGridDist;
										timeStep = timeStep*2;
										tmp1 = selGridLineCoord.y.round(timeStep/numGridLinesPerUnit);
									};

									(tmp1 > selGridLineCoord.y).if {
										selGridLineCoord = (selGridLineCoord.x + ((tmp1 - selGridLineCoord.y)/oldTimeIncr*vhorzGridDist))@tmp1
									} {
										(tmp1 < selGridLineCoord.y).if {
											selGridLineCoord = (selGridLineCoord.x - ((selGridLineCoord.y - tmp1)/oldTimeIncr*vhorzGridDist))@tmp1
										}
									};
									quantCoord.y = quantCoord.y.trunc(timeStep);
									timeIncr = timeStep/numGridLinesPerUnit;
									remNumGridLines = ((selGridLineCoord.y - quantCoord.y)/timeIncr).round(1);
									(remNumGridLines >= numGridLinesPerUnit).if {
										remNumGridLines = remNumGridLines - numGridLinesPerUnit;
										quantCoord.y = quantCoord.y + timeStep
									};
									quantCoord.x = selGridLineCoord.x - (remNumGridLines*vhorzGridDist);

									scaleCount = scaleCount + 1;
									(scaleCount > 2).if { scaleCount = 0 }

								}
							}
						}
					};

					(domainMode == \unipolar).if {
						currZeroPos = quantCoord.x - ((quantCoord.y/timeStep)*(numGridLinesPerUnit*vhorzGridDist)).round(1);
						(currZeroPos > 0).if {
							selGridLineCoord.x = selGridLineCoord.x - currZeroPos;
							quantCoord.x = quantCoord.x - currZeroPos
						}
					};

					// bit ugly, but will do for now...
					envData.timeStep = timeStep;
					envData.refCoord = selGridLineCoord;
					envData.unitDist = numGridLinesPerUnit*vhorzGridDist;
					envData.height = me.bounds.height;
					envData.updateAllBreakPointCoords;
					envData.updateAllCurvePointCoords

				}

			};

			prevPoint = x@y;
			scaleRespCount = scaleRespCount + 1;
			(scaleRespCount >= scaleResponsiveness).if { scaleRespCount = 0 };
			me.refresh
		});

		view.layout_(
			GridLayout.rows(
				[nil,settingsView],
				[[rangeView,rows:3]],
				[nil,envView]
			).hSpacing_(0).setColumnStretch(0,1).setColumnStretch(1,20).setRowStretch(0,5).setRowStretch(1,1).setRowStretch(2,74).setRowStretch(3,1)
		);

		this.env_(env,false)
	}

	background_ { arg newBackground,refreshFlag = true;
		envView.background_(newBackground);
		refreshFlag.if { envView.refresh }
	}

	horzGridDist_ { arg newHorzGridDist,refreshFlag = true;
		horzGridDist = newHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	maxHorzGridDist_ { arg newMaxHorzGridDist,refreshFlag = true;
		maxHorzGridDist = newMaxHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	minHorzGridDist_ { arg newMinHorzGridDist,refreshFlag = true;
		minHorzGridDist = newMinHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	numVertGridLines_ { arg newNumVertGridLines,refreshFlag = true;
		numVertGridLines = newNumVertGridLines;
		refreshFlag.if { envView.refresh }
	}

	gridColor_ { arg newGridColor,refreshFlag = true;
		gridColor = newGridColor;
		refreshFlag.if { envView.refresh }
	}

	gridWidth_ { arg newGridWidth,refreshFlag = true;
		gridWidth = newGridWidth;
		refreshFlag.if { envView.refresh }
	}

	showHorzAxis_ { arg newShowHorzAxis,refreshFlag = true;
		showHorzAxis = newShowHorzAxis;
		refreshFlag.if { envView.refresh }
	}

	showVertAxis_ { arg newShowVertAxis,refreshFlag = true;
		showVertAxis = newShowVertAxis;
		refreshFlag.if { envView.refresh }
	}

	breakPointSize_ { arg newBreakPointSize,refreshFlag = true;
		breakPointSize = newBreakPointSize;
		refreshFlag.if { envView.refresh }
	}

	curvePointRadius_ { arg newCurvePointRadius,refreshFlag = true;
		curvePointRadius = newCurvePointRadius;
		refreshFlag.if { envView.refresh }
	}

	font_ { arg newFont,refreshFlag = true;
		font = newFont;
		refreshFlag.if { settingsView.refresh; rangeView.refresh; envView.refresh }
	}

	unitMode_ { arg newUnitMode,refreshFlag = true;
		unitMode = newUnitMode;
		refreshFlag.if { envView.refresh }
	}

	domainMode_ { arg newDomainMode,refreshFlag = true;
		#[\unipolar,\bipolar].includes(newDomainMode).if {
			domainMode = newDomainMode;
			refreshFlag.if { envView.refresh }
		} {
			"domainMode is not a valid choice".warn
		}
	}

	minRange_ { arg newMinRange,refreshFlag = true;
		minRange = newMinRange;
		refreshFlag.if { rangeView.refresh }
	}

	maxRange_ { arg newMaxRange,refreshFlag = true;
		maxRange = newMaxRange;
		refreshFlag.if { rangeView.refresh }
	}

	scaleResponsiveness_ { arg newScaleResponsiveness;
		scaleResponsiveness = newScaleResponsiveness.asInteger.max(1)
	}

	env_ { arg newEnv,refreshFlag = true;
		newEnv.isKindOf(Env).if {
			env = newEnv;
			envData = EnvPlotData(env,timeStep,selGridLineCoord,envView.bounds.height,minRange,maxRange,numGridLinesPerUnit*vhorzGridDist);
			refreshFlag.if { envView.refresh }
		} {
			Error("arg env has to be an instance of %\n".format(Env.name)).throw
		}
	}

	drawFunc_ { arg newDrawFunc,refreshFlag = true;
		drawFunc = newDrawFunc;
		refreshFlag.if { envView.refresh }
	}

	prMakeStr { arg number,prec = 4;
		var str = number.asFloat.asStringPrec(prec);
		(number.frac < 1e-9).if { str = str ++ ".0" };
		^str
	}

}

EnvPlotData {
	var <>env,<>breakPointCoords,<>curvePointCoords,<>timeStep,<>refCoord,<>height,<>unitDist,<>minLevel,<>maxLevel,<>selBreakPoint;

	*new { arg env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint;
		^super.new.init(env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint)
	}

	init { arg env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint;
		this.env = env ? Env([0,1,1,0],[0,0.5,0],[0,0,0],nil,nil);
		this.timeStep = timeStep ? 0.2;
		this.refCoord = refCoord ? 300@0.6;
		this.height = height ? 300;
		this.unitDist = unitDist ? 100;
		this.minLevel = minLevel ? this.env.levels.minItem;
		this.maxLevel = maxLevel ? this.env.levels.maxItem;
		this.selBreakPoint = selBreakPoint ? 1;
		this.updateAllBreakPointCoords;
		this.updateAllCurvePointCoords
	}

	updateAllBreakPointCoords {
		var xc,yc;
		yc = env.levels collect: _.linlin(minLevel,maxLevel,height,0);
		xc = [0] ++ env.times.integrate collect: { |t|
			(t == refCoord.y).if {
				refCoord.x
			} {
				refCoord.x - ((refCoord.y - t)/timeStep*unitDist).round(1)
			}
		};
		breakPointCoords = [xc,yc].flop collect: _.asPoint
	}

	updateAllCurvePointCoords {
		var xc,yc;
		curvePointCoords = { Point() } ! (breakPointCoords.size - 1);
		 (breakPointCoords.size - 1) do: { |i|
			this.calcXCurvePoint(i);
			this.calcYCurvePoint(i)
		}
	}

	calcXCurvePoint { arg idx;
		curvePointCoords[idx].x = (breakPointCoords[idx].x + breakPointCoords[idx + 1].x)/2
	}

	calcYCurvePoint { arg idx,inc;
		(breakPointCoords[idx + 1].y > breakPointCoords[idx].y).if {
			inc.notNil.if {
				//env.curves[idx] = (env.curves[idx] - (inc.sign*0.25)).clip(-18,18)
				env.setCurve(idx,(env.curves[idx] - (inc.sign*0.25)).clip(-18,18))
			};
			curvePointCoords[idx].y = breakPointCoords[idx].y - (env.curves[idx].asWarp.map(0.5)*(breakPointCoords[idx].y - breakPointCoords[idx + 1].y))
		} {
			inc.notNil.if {
				//env.curves[idx] = (env.curves[idx] + (inc.sign*0.25)).clip(-18,18)
				env.setCurve(idx,(env.curves[idx] + (inc.sign*0.25)).clip(-18,18))
			};
			curvePointCoords[idx].y = breakPointCoords[idx + 1].y - (env.curves[idx].neg.asWarp.map(0.5)*(breakPointCoords[idx + 1].y - breakPointCoords[idx].y))
		}
	}

}

// hack to ensure envelope state is properly updated
+ Env {

	setLevel { arg idx,value;
		(idx == 0).if {
			this.levels = [value] ++ this.levels[idx + 1.. this.levels.lastIndex]
		} {
			(idx == this.levels.lastIndex).if {
				this.levels = this.levels[0..idx - 1] ++ [value]
			} {
				this.levels = this.levels[0..idx - 1] ++ [value] ++ this.levels[idx + 1.. this.levels.lastIndex]
			}
		}
	}

	setTime { arg idx,value;
		(idx == 0).if {
			this.times = [value] ++ this.times[idx + 1.. this.times.lastIndex]
		} {
			(idx == this.times.lastIndex).if {
				this.times = this.times[0..idx - 1] ++ [value]
			} {
				this.times = this.times[0..idx - 1] ++ [value] ++ this.times[idx + 1.. this.times.lastIndex]
			}
		}
	}

	setCurve { arg idx,value;
		(idx == 0).if {
			this.curves = [value] ++ this.curves[idx + 1.. this.curves.lastIndex]
		} {
			(idx == this.curves.lastIndex).if {
				this.curves = this.curves[0..idx - 1] ++ [value]
			} {
				this.curves = this.curves[0..idx - 1] ++ [value] ++ this.curves[idx + 1.. this.curves.lastIndex]
			}
		}
	}

}